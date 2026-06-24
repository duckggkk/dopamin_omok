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
 * 난이도는 1~7 사다리. "얼마나 멀리 보고, 얼마나 실수하고, 얼마나 무작위인가"로 갈린다.
 * 곡선을 가파르게 잡아 1단계만 입문용이고 2단계부터는 빈틈 없이 막는다(예전엔 3단계까지 너무 쉬웠음).
 *  - 1 단계: 거의 아무 데나 둠(즉시 패배만 가끔 막음) — 입문용
 *  - 2~3 단계: 한 수 앞 휴리스틱 — 즉시 위협은 모두 막고 자기 모양도 키움
 *  - 4~7 단계: 휴리스틱 + 상대 반격까지 한 겹 더 읽기(이중 위협). 무작위성 0 으로 수렴.
 *  - 6~7 단계는 사람이 후공(백)이라 선공 이점이 없다 → 7단계는 사실상 최종 보스.
 */

export interface AiMove {
  row: number;
  col: number;
}

/** 한 단계의 표시 정보 + 전략 파라미터(엔진과 UI가 함께 쓰는 단일 출처). */
export interface AiLevel {
  level: number;          // 1~9
  label: string;
  emoji: string;
  desc: string;
  /** 8~9 단계: 사람이 백(후공). AI 가 흑(선공)으로 먼저 둔다. */
  humanPlaysWhite: boolean;
  // ── 전략 파라미터 ──
  mode: 'random' | 'heuristic' | 'deep';
  blockWinProb: number;   // random 모드에서 상대 즉승 자리를 실제로 막을 확률
  defenseWeight: number;  // 수비 점수 가중치(높을수록 잘 막음)
  jitter: number;         // 동점 처리용 무작위 크기(클수록 들쭉날쭉)
  topK: number;           // deep 모드에서 깊게 읽을 상위 후보 수
}

/**
 * 7단계 사다리. 위로 갈수록 강하다. (백엔드 AiProgressService.MAX_LEVEL=7 과 길이 일치 필수)
 * 곡선을 가파르게: 1단계만 무작위, 2단계부터 1수 휴리스틱, 4단계부터 2수 읽기, 6~7단계는 사람 후공.
 */
export const AI_LEVELS: AiLevel[] = [
  { level: 1, label: '1단계 · 새싹',   emoji: '🌱', desc: '오목을 막 배운 상대. 빈틈이 많아요.',
    humanPlaysWhite: false, mode: 'random',    blockWinProb: 0.65, defenseWeight: 0.5,  jitter: 460, topK: 0 },
  { level: 2, label: '2단계 · 견습',   emoji: '🪵', desc: '기본기를 갖춘 상대. 위협은 빠짐없이 막아요.',
    humanPlaysWhite: false, mode: 'heuristic', blockWinProb: 1,    defenseWeight: 0.85, jitter: 90,  topK: 0 },
  { level: 3, label: '3단계 · 숙련',   emoji: '⚔️', desc: '공수 균형이 잡힌 상대. 빈틈이 거의 없어요.',
    humanPlaysWhite: false, mode: 'heuristic', blockWinProb: 1,    defenseWeight: 1.0,  jitter: 24,  topK: 0 },
  { level: 4, label: '4단계 · 고수',   emoji: '🔥', desc: '한 수 앞을 읽고 반격을 노리는 상대.',
    humanPlaysWhite: false, mode: 'deep',      blockWinProb: 1,    defenseWeight: 1.0,  jitter: 12,  topK: 12 },
  { level: 5, label: '5단계 · 달인',   emoji: '🧠', desc: '이중 위협을 만들고 빈틈을 파고드는 상대.',
    humanPlaysWhite: false, mode: 'deep',      blockWinProb: 1,    defenseWeight: 1.05, jitter: 4,   topK: 16 },
  { level: 6, label: '6단계 · 패왕',   emoji: '👑', desc: '여기부터 당신은 후공(백). 실수를 용서하지 않아요.',
    humanPlaysWhite: true,  mode: 'deep',      blockWinProb: 1,    defenseWeight: 1.1,  jitter: 0,   topK: 20 },
  { level: 7, label: '7단계 · 최종 보스', emoji: '🐉', desc: '당신은 후공(백). 거의 이길 수 없는 최강의 상대.',
    humanPlaysWhite: true,  mode: 'deep',      blockWinProb: 1,    defenseWeight: 1.2,  jitter: 0,   topK: 26 },
];

export const MAX_AI_LEVEL = AI_LEVELS.length;

/** level(1~9) → 설정. 범위를 벗어나면 가장 가까운 단계로 보정. */
const levelConfig = (level: number): AiLevel => {
  const idx = Math.min(MAX_AI_LEVEL - 1, Math.max(0, Math.round(level) - 1));
  return AI_LEVELS[idx];
};

// ──────────────────────────────────────────────────────────────────────────
// 모양 평가 (한 칸의 위협도)
// ──────────────────────────────────────────────────────────────────────────

const DIRECTIONS: ReadonlyArray<readonly [number, number]> = [
  [0, 1],
  [1, 0],
  [1, 1],
  [1, -1],
];

// '길이 5 창' 안에 든 내 돌 수 → 가치표. 5목 > 4 > 3 > 2 > 1 순으로 크게 벌린다.
const windowValue = (selfCount: number): number => {
  switch (selfCount) {
    case 5: return 1_000_000; // 오목 완성(실제 승리는 checkWin 으로 따로 확정)
    case 4: return 100_000;   // 한 수면 오목 — 상대는 강제로 막아야 함
    case 3: return 8_000;
    case 2: return 400;
    case 1: return 40;
    default: return 0;
  }
};

/**
 * (row,col) 에 color 돌을 "놓는다고 가정"했을 때, 한 방향(dRow,dCol)에서의 위협도.
 *
 * 놓는 칸을 중심으로 그 칸을 지나는 '길이 5 창' 5개를 본다.
 * 상대 돌/벽이 끼지 않은 '깨끗한 창'만 오목 가능성이 있으므로, 그런 창에 든 내 돌 수로 점수를 매긴다.
 * 이 방식은 중간이 한 칸 빈 끊긴 모양(예: ●●_●●)도 자연히 4로 잡아낸다
 * — 연속만 세던 기존 방식이 과소평가하던 약점을 보완(고단계가 빈틈에 안 당하게).
 *
 * 같은 최고 점수를 내는 깨끗한 창이 둘 이상이면 양방향으로 열린 모양(열린4·열린3)이므로 가산한다.
 */
const directionScore = (
  board: Board,
  row: number,
  col: number,
  color: StoneColor,
  dRow: number,
  dCol: number,
): number => {
  let best = 0;
  let waysAtBest = 0;

  // 창의 시작 오프셋 s = -4..0 → 각 창은 [s, s+4] 로 항상 중심(0)을 포함한다.
  for (let s = -4; s <= 0; s++) {
    let selfCount = 0;
    let clean = true;

    for (let k = 0; k < 5; k++) {
      const off = s + k;
      if (off === 0) {
        selfCount++; // 중심 = 지금 놓는 돌
        continue;
      }
      const r = row + dRow * off;
      const c = col + dCol * off;
      if (!inBounds(r, c)) { clean = false; break; }
      const cell = board[r][c];
      if (cell === color) selfCount++;
      else if (cell !== null) { clean = false; break; } // 상대 돌 → 이 창은 막혀 오목 불가
      // null = 빈칸(아직 채울 수 있음)
    }

    if (!clean) continue;
    if (selfCount > best) { best = selfCount; waysAtBest = 1; }
    else if (selfCount === best && best > 0) waysAtBest++;
  }

  if (best === 0) return 0;
  let value = windowValue(best);
  if (waysAtBest >= 2) value *= 2; // 열린(양방향) 모양 강조
  return value;
};

/** 네 방향 위협도 합. 두 방향에서 동시에 위협이 생기면(이중 위협) 합이 커져 자연히 좋은 수로 평가된다. */
const attackScore = (board: Board, row: number, col: number, color: StoneColor): number => {
  let total = 0;
  for (const [dRow, dCol] of DIRECTIONS) {
    total += directionScore(board, row, col, color, dRow, dCol);
  }
  return total;
};

/**
 * 후보 칸 생성 — 빈 보드는 15×15 전체지만,
 * 이미 놓인 돌 주변(반경 radius)만 보면 충분하고 훨씬 빠르다.
 */
const candidateCells = (board: Board, radius = 2): AiMove[] => {
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

  const result: AiMove[] = [];
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

/** 이 칸에 color 가 두면 즉시 오목(승리)인가? — 정확한 승리 판정은 checkWin 에 위임. */
const isWinningCell = (board: Board, row: number, col: number, color: StoneColor): boolean =>
  checkWin(placeStone(board, row, col, color), row, col);

/** 한 수 앞 종합 점수 = 내 공격 + 상대 위협 차단 가치(수비). 수비 가중치는 단계별로 다르다. */
const cellValue = (
  board: Board,
  cell: AiMove,
  ai: StoneColor,
  human: StoneColor,
  defenseWeight: number,
): number =>
  attackScore(board, cell.row, cell.col, ai) +
  attackScore(board, cell.row, cell.col, human) * defenseWeight;

/** 배열에서 가장 점수가 높은 칸을 고른다(동점이면 jitter 만큼의 무작위로 자연스럽게). */
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
// 단계별 전략
// ──────────────────────────────────────────────────────────────────────────

/** random: 자기 마무리는 챙기고, 상대 즉승은 blockWinProb 확률로만 막는다. 나머지는 거의 무작위. */
const chooseRandom = (
  board: Board, ai: StoneColor, human: StoneColor, cells: AiMove[], cfg: AiLevel,
): AiMove => {
  for (const cell of cells) {
    if (isWinningCell(board, cell.row, cell.col, ai)) return cell;
  }
  if (Math.random() < cfg.blockWinProb) {
    for (const cell of cells) {
      if (isWinningCell(board, cell.row, cell.col, human)) return cell;
    }
  }
  const scored = cells.map((cell) => ({
    cell,
    score: attackScore(board, cell.row, cell.col, ai) * 0.5,
  }));
  return pickBest(scored, cfg.jitter);
};

/** heuristic: 한 수 앞 휴리스틱. 즉시 위협(내 승리·상대 승리)은 빠짐없이 처리한다. */
const chooseHeuristic = (
  board: Board, ai: StoneColor, human: StoneColor, cells: AiMove[], cfg: AiLevel,
): AiMove => {
  for (const cell of cells) {
    if (isWinningCell(board, cell.row, cell.col, ai)) return cell;
  }
  for (const cell of cells) {
    if (isWinningCell(board, cell.row, cell.col, human)) return cell;
  }
  const scored = cells.map((cell) => ({
    cell,
    score: cellValue(board, cell, ai, human, cfg.defenseWeight),
  }));
  return pickBest(scored, cfg.jitter);
};

/**
 * deep: 휴리스틱 + 한 겹 더 읽기.
 * 내가 둔 뒤 "상대의 최선의 반격"이 얼마나 강한지 빼서, 상대에게 열린4를 내주는 수를 피한다.
 * 결과적으로 이중 위협(양수겸장)을 만들고 상대 위협을 미리 차단하는 단단한 수를 둔다.
 * 계산량 절약: 1수 점수 상위 topK 후보만 깊게 본다(topK 클수록 강하고 느림).
 */
const chooseDeep = (
  board: Board, ai: StoneColor, human: StoneColor, cells: AiMove[], cfg: AiLevel,
): AiMove => {
  for (const cell of cells) {
    if (isWinningCell(board, cell.row, cell.col, ai)) return cell;
  }
  for (const cell of cells) {
    if (isWinningCell(board, cell.row, cell.col, human)) return cell;
  }

  const ranked = cells
    .map((cell) => ({ cell, score: cellValue(board, cell, ai, human, cfg.defenseWeight) }))
    .sort((a, b) => b.score - a.score)
    .slice(0, cfg.topK);

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

  return pickBest(scored, cfg.jitter);
};

/**
 * 현재 보드에서 AI(=ai 색)가 둘 한 수를 결정한다.
 * 둘 곳이 없으면 null(보드가 꽉 찼거나 비정상 상태).
 */
export const chooseAiMove = (
  board: Board,
  ai: StoneColor,
  level: number,
): AiMove | null => {
  const cfg = levelConfig(level);
  const human = opposite(ai);
  const cells = candidateCells(board);
  if (cells.length === 0) return null;

  switch (cfg.mode) {
    case 'random':
      return chooseRandom(board, ai, human, cells, cfg);
    case 'heuristic':
      return chooseHeuristic(board, ai, human, cells, cfg);
    case 'deep':
    default:
      return chooseDeep(board, ai, human, cells, cfg);
  }
};
