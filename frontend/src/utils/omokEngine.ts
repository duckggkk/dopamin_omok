import { Board, StoneColor } from '@/types';
import { BOARD_SIZE } from '@/constants/board';

/**
 * 클라이언트 전용(오프라인) 오목 규칙 엔진.
 *
 * 멀티플레이 클래식 오목의 승패 판정은 백엔드 OmokGameEngine 이 담당하지만,
 * 싱글플레이(AI 연습)는 서버를 거치지 않고 브라우저에서 한 판이 끝까지 돌아가야 한다.
 * 그래서 백엔드와 "정확히 5목만 승리(6목 이상 장목은 무효)" 규칙을 동일하게 맞춰 옮겨 둔다.
 * 금수(렌주룰)는 적용하지 않는다 — 연습 모드는 항상 자유룰(FREESTYLE).
 */

// 가로 · 세로 · 두 대각선. (반대 방향은 부호를 뒤집어 함께 센다)
const DIRECTIONS: ReadonlyArray<readonly [number, number]> = [
  [0, 1],
  [1, 0],
  [1, 1],
  [1, -1],
];

export const inBounds = (row: number, col: number): boolean =>
  row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;

/** (row,col) 에서 (dRow,dCol) 방향으로 같은 색이 몇 칸 연속되는지 센다(시작칸 제외). */
const countInDirection = (
  board: Board,
  row: number,
  col: number,
  dRow: number,
  dCol: number,
  color: StoneColor,
): number => {
  let count = 0;
  let r = row + dRow;
  let c = col + dCol;
  while (inBounds(r, c) && board[r][c] === color) {
    count++;
    r += dRow;
    c += dCol;
  }
  return count;
};

/**
 * (row,col) 에 놓인 돌이 오목을 완성했는지 검사.
 * 백엔드와 동일하게 "정확히 5목"일 때만 승리로 본다.
 */
export const checkWin = (board: Board, row: number, col: number): boolean => {
  const color = board[row][col];
  if (!color) return false;

  for (const [dRow, dCol] of DIRECTIONS) {
    const count =
      1 +
      countInDirection(board, row, col, dRow, dCol, color) +
      countInDirection(board, row, col, -dRow, -dCol, color);
    if (count === 5) return true;
  }
  return false;
};

export const isBoardFull = (board: Board): boolean =>
  board.every((rowArr) => rowArr.every((cell) => cell !== null));

/** board 를 복제해 (row,col) 에 color 를 놓은 새 보드를 반환(원본 불변). */
export const placeStone = (
  board: Board,
  row: number,
  col: number,
  color: StoneColor,
): Board => {
  const next = board.map((r) => [...r]);
  next[row][col] = color;
  return next;
};

export const opposite = (color: StoneColor): StoneColor =>
  color === 'BLACK' ? 'WHITE' : 'BLACK';
