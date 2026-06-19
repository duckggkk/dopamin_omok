import { Board, StoneColor } from '@/types';
import { BOARD_SIZE } from '@/constants/board';
import { checkWin, inBounds, opposite, placeStone } from '@/utils/omokEngine';

/**
 * 싱글플레이용 오목 AI (전부 브라우저에서 계산 — 서버/레이팅과 무관).
 *
 * 핵심 아이디어 = "각 빈 칸에 점수를 매겨 제일 높은 칸에 둔다".
 *  - 공격 점수: 내가 거기 두면 얼마나 좋은 모양(열린3·4 등)이 되는가
 *  - 수비 점수: 상대가 거기 두면 얼마나 위협적인가 (= 그 칸을 막을 가치)
 * 두 점수를 합쳐 가장 큰 칸을 고르면, 자연스럽게 "이기는 수는 두고 / 막을 곳은 막는" 플레이가 된다.
 *
 * 난이도는 "얼마나 멀리 보고, 얼마나 실수하는가"로 갈린다.
 *  - EASY  : 자기 마무리 수만 챙기고 위협은 자주 놓침 + 무작위성 ↑ (초보가 이길 수 있음)
 *  - NORMAL: 한 수 앞 휴리스틱 — 즉시 위협은 모두 막고 자기 모양도 키움
 *  - HARD  : NORMAL + 상대의 다음 반격까지 한 겹 더 읽어 양수겸장(이중 위협)을 노림
 */

export type AiDifficulty = 'EASY' | 'NORMAL' | 'HARD';

export interface AiMove {
  row: number;
  col: number;
}

// 한 줄(방향)에서 만들어지는 모양의 가치표.
// n = 그 방향으로 이어지는 내 돌 개수(놓을 돌 포함), open = 양 끝 중 비어 있는(이어갈 수 있는) 쪽 수.
// 5목(승리) > 열린4 > 4(막힌4)·열린3 > … 순으로 가중치를 크게 벌려 둔다.
const shapeScore = (n: number, openEnds: number): number => {
  if (n >= 5) return 1_000_000; // 오목 완성 (실제 승리 여부는 checkWin 으로 따로 확정)
  if (n === 4) return openEnds === 2 ? 100_000 : 12_000; // 열린4=사실상 승리 / 막힌4=상대 강제 수비
  if (n === 3) return openEnds === 2 ? 8_000 : 600;       // 열린3=강한 위협 / 막힌3
  if (n === 2) return openEnds === 2 ? 400 : 60;
  if (n === 1) return openEnds === 2 ? 40 : 8;
  return 0;
};

/**
 * (row,col) 에 color 돌을 "놓는다고 가정"했을 때의 공격 점수.
 * 4개 방향 각각의 모양 점수를 합산한다 — 두 방향에서 동시에 위협이 생기면(이중 위협)
 * 합이 커져 자연히 더 좋은 수로 평가된다.
 *
 * 단순화: 사이가 빈 끊긴 모양(예: ●○●●)은 연속으로만 세어 일부 과소평가한다.
 * 연습용 AI 로는 충분하며, 더 정교하게 하려면 빈칸 한 칸을 끼운 패턴까지 봐야 한다.
 */
const attackScore = (board: Board, row: number, col: number, color: StoneColor): number => {
  const directions: ReadonlyArray<readonly [number, number]> = [
    [0, 1],
    [1, 0],
    [1, 1],
    [1, -1],
  ];

  let total = 0;
  for (const [dRow, dCol] of directions) {
    let count = 1; // 지금 놓는 돌
    let openEnds = 0;

    // 정방향으로 같은 색을 세고, 끊긴 자리가 비어 있으면 '열린 끝'으로 친다.
    for (const sign of [1, -1] as const) {
      let r = row + dRow * sign;
      let c = col + dCol * sign;
      while (inBounds(r, c) && board[r][c] === color) {
        count++;
        r += dRow * sign;
        c += dCol * sign;
      }
      if (inBounds(r, c) && board[r][c] === null) openEnds++;
    }

    total += shapeScore(count, openEnds);
  }
  return total;
};

/**
 * 후보 칸 생성 — 빈 보드는 15×15 전체지만,
 * 이미 놓인 돌 주변(반경 radius)만 보면 충분하고 훨씬 빠르다.
 */
const candidateCells = (board: Board, radius = 2): AiMove[] => {
  const result: AiMove[] = [];
  let hasStone = false;

  for (let r = 0; r < BOARD_SIZE; r++) {
    for (let c = 0; c < BOARD_SIZE; c++) {
      if (board[r][c] !== null) hasStone = true;
    }
  }

  // 첫 수: 한가운데
  if (!hasStone) {
    const mid = Math.floor(BOARD_SIZE / 2);
    return [{ row: mid, col: mid }];
  }

  const seen = new Set<number>();
  for (let r = 0; r < BOARD_SIZE; r++) {
    for (let c = 0; c < BOARD_SIZE; c++) {
      if (board[r][c] === null) continue;
      for (let dr = -radius; dr <= radius; dr++) {
        for (let dc = -radius; dc <= radius; dc++) {
          const nr = r + dr;
          const nc = c + dc;
          if (!inBounds(nr, nc) || board[nr][nc] !== null) continue;
          const key = nr * BOARD_SIZE + nc;
          if (seen.has(key)) continue;
          seen.add(key);
          result.push({ row: nr, col: nc });
        }
      }
    }
  }
  return result;
};

/** 이 칸에 color 가 두면 즉시 오목(승리)인가? */
const isWinningCell = (board: Board, row: number, col: number, color: StoneColor): boolean =>
  checkWin(placeStone(board, row, col, color), row, col);

/** 한 수 앞 종합 점수 = 내 공격 + 상대 위협 차단 가치(수비). */
const cellValue = (board: Board, cell: AiMove, ai: StoneColor, human: StoneColor): number => {
  const offense = attackScore(board, cell.row, cell.col, ai);
  const defense = attackScore(board, cell.row, cell.col, human);
  // 수비를 살짝(0.95) 낮게 둬 동점일 땐 공격을 선호 — 끌려다니지 않게 한다.
  return offense + defense * 0.95;
};

/** 배열에서 가장 점수가 높은 칸을 고른다(동점이면 약간의 무작위로 자연스럽게). */
const pickBest = (scored: Array<{ cell: AiMove; score: number }>, jitter = 0): AiMove => {
  let best = scored[0];
  for (const s of scored) {
    const noisy = s.score + (jitter ? Math.random() * jitter : 0);
    const bestNoisy = best.score + (jitter ? Math.random() * jitter : 0);
    if (noisy > bestNoisy) best = s;
  }
  return best.cell;
};

// ──────────────────────────────────────────────────────────────────────────
// 난이도별 전략
// ──────────────────────────────────────────────────────────────────────────

/** 쉬움: 자기 마무리만 챙기고, 상대의 '4(한 수면 짐)'만 가끔 막는다. 나머지는 거의 무작위. */
const chooseEasy = (board: Board, ai: StoneColor, human: StoneColor, cells: AiMove[]): AiMove => {
  // 1) 내가 바로 이길 수 있으면 둔다(보기 답답하지 않게).
  for (const cell of cells) {
    if (isWinningCell(board, cell.row, cell.col, ai)) return cell;
  }
  // 2) 상대가 바로 이기는 자리는 60% 확률로만 막는다(자주 실수 → 초보가 이김).
  if (Math.random() < 0.6) {
    for (const cell of cells) {
      if (isWinningCell(board, cell.row, cell.col, human)) return cell;
    }
  }
  // 3) 그 외엔 돌 주변 아무 칸이나(약간의 선호만).
  const scored = cells.map((cell) => ({
    cell,
    score: attackScore(board, cell.row, cell.col, ai) * 0.5,
  }));
  return pickBest(scored, 500); // jitter 큼 → 들쭉날쭉
};

/** 보통: 한 수 앞 휴리스틱. 즉시 위협은 빠짐없이 처리한다. */
const chooseNormal = (board: Board, ai: StoneColor, human: StoneColor, cells: AiMove[]): AiMove => {
  for (const cell of cells) {
    if (isWinningCell(board, cell.row, cell.col, ai)) return cell; // 이기는 수 최우선
  }
  for (const cell of cells) {
    if (isWinningCell(board, cell.row, cell.col, human)) return cell; // 지는 수 막기
  }
  const scored = cells.map((cell) => ({ cell, score: cellValue(board, cell, ai, human) }));
  return pickBest(scored, 10);
};

/**
 * 어려움: 보통 휴리스틱 + 한 겹 더 읽기.
 * 내가 둔 뒤 "상대의 최선의 반격"이 얼마나 강한지 빼서, 상대에게 열린4를 내주는 수를 피한다.
 * 결과적으로 이중 위협(양수겸장)을 만들고 상대 위협을 미리 차단하는 단단한 수를 둔다.
 */
const chooseHard = (board: Board, ai: StoneColor, human: StoneColor, cells: AiMove[]): AiMove => {
  for (const cell of cells) {
    if (isWinningCell(board, cell.row, cell.col, ai)) return cell;
  }
  for (const cell of cells) {
    if (isWinningCell(board, cell.row, cell.col, human)) return cell;
  }

  // 계산량 절약: 1수 점수 상위 후보만 깊게 본다.
  const ranked = cells
    .map((cell) => ({ cell, score: cellValue(board, cell, ai, human) }))
    .sort((a, b) => b.score - a.score)
    .slice(0, 12);

  const scored = ranked.map(({ cell, score }) => {
    const afterMyMove = placeStone(board, cell.row, cell.col, ai);

    // 내가 둔 다음, 상대가 둘 수 있는 가장 강한 한 수의 위협도를 구한다.
    let opponentBest = 0;
    for (const reply of candidateCells(afterMyMove)) {
      if (isWinningCell(afterMyMove, reply.row, reply.col, human)) {
        opponentBest = 1_000_000; // 내가 두면 상대가 바로 이겨버리는 자리 → 강하게 회피
        break;
      }
      const threat = attackScore(afterMyMove, reply.row, reply.col, human);
      if (threat > opponentBest) opponentBest = threat;
    }

    // 내 이득은 살리되, 상대에게 넘겨주는 반격은 깎는다.
    return { cell, score: score - opponentBest * 0.9 };
  });

  return pickBest(scored, 5);
};

/**
 * 현재 보드에서 AI(=ai 색)가 둘 한 수를 결정한다.
 * 둘 곳이 없으면 null(보드가 꽉 찼거나 비정상 상태).
 */
export const chooseAiMove = (
  board: Board,
  ai: StoneColor,
  difficulty: AiDifficulty,
): AiMove | null => {
  const human = opposite(ai);
  const cells = candidateCells(board);
  if (cells.length === 0) return null;

  switch (difficulty) {
    case 'EASY':
      return chooseEasy(board, ai, human, cells);
    case 'HARD':
      return chooseHard(board, ai, human, cells);
    case 'NORMAL':
    default:
      return chooseNormal(board, ai, human, cells);
  }
};
