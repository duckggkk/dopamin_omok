import { describe, it, expect } from 'vitest';
import { checkWin, isBoardFull, placeStone, opposite, inBounds } from './omokEngine';
import { createEmptyBoard, BOARD_SIZE } from '@/constants/board';
import { Board, StoneColor } from '@/types';

/**
 * 이 엔진은 싱글플레이(AI 연습) 전용이지만 백엔드 OmokGameEngine 과 규칙이 같아야 한다.
 * 특히 "정확히 5목만 승리(6목 이상 장목은 무효)"가 어긋나면 같은 국면을 두고
 * 온라인 대국과 AI 대국의 판정이 갈린다 — 그래서 장목 케이스를 반드시 고정해 둔다.
 */

/** (row, col) 부터 (dRow, dCol) 방향으로 count 개의 돌을 놓은 보드를 만든다. */
const withLine = (
  startRow: number,
  startCol: number,
  dRow: number,
  dCol: number,
  count: number,
  color: StoneColor = 'BLACK',
): Board => {
  const board = createEmptyBoard();
  for (let i = 0; i < count; i++) {
    board[startRow + dRow * i][startCol + dCol * i] = color;
  }
  return board;
};

/** 빈 칸 없이 가득 찬 보드. (Board 는 null 을 허용하므로 타입을 명시해 둔다) */
const filledBoard = (): Board =>
  createEmptyBoard().map((row) => row.map((): StoneColor | null => 'BLACK'));

describe('checkWin', () => {
  it.each([
    ['가로', 0, 1],
    ['세로', 1, 0],
    ['↘ 대각선', 1, 1],
    ['↗ 대각선', 1, -1],
  ])('%s 방향으로 5목이면 승리', (_name, dRow, dCol) => {
    // 어느 방향이든 보드 안에 들어오도록 중앙에서 시작
    const board = withLine(7, 7, dRow, dCol, 5);
    expect(checkWin(board, 7, 7)).toBe(true);
  });

  it('마지막에 놓은 돌이 줄의 중간이어도 승리로 인정한다', () => {
    const board = withLine(7, 3, 0, 1, 5); // (7,3)~(7,7)
    expect(checkWin(board, 7, 5)).toBe(true);
  });

  it('4목은 승리가 아니다', () => {
    const board = withLine(7, 3, 0, 1, 4);
    expect(checkWin(board, 7, 3)).toBe(false);
  });

  it('6목(장목)은 승리가 아니다 — 백엔드와 동일하게 정확히 5목만 인정', () => {
    const board = withLine(7, 3, 0, 1, 6);
    // 줄 안의 어느 돌을 기준으로 판정해도 승리가 아니어야 한다
    for (let c = 3; c <= 8; c++) {
      expect(checkWin(board, 7, c)).toBe(false);
    }
  });

  it('상대 돌이 끼어들어 끊기면 승리가 아니다', () => {
    const board = withLine(7, 3, 0, 1, 5);
    board[7][5] = 'WHITE';
    expect(checkWin(board, 7, 3)).toBe(false);
  });

  it('서로 다른 색 5개가 늘어선 것은 승리가 아니다', () => {
    const board = createEmptyBoard();
    (['BLACK', 'BLACK', 'WHITE', 'BLACK', 'BLACK'] as StoneColor[]).forEach((color, i) => {
      board[7][3 + i] = color;
    });
    expect(checkWin(board, 7, 5)).toBe(false);
  });

  it('빈 칸을 기준으로 검사하면 false', () => {
    expect(checkWin(createEmptyBoard(), 7, 7)).toBe(false);
  });

  it('보드 경계에 붙은 5목도 승리로 인정한다', () => {
    const topLeft = withLine(0, 0, 0, 1, 5);
    expect(checkWin(topLeft, 0, 0)).toBe(true);

    const bottomRight = withLine(BOARD_SIZE - 1, BOARD_SIZE - 5, 0, 1, 5);
    expect(checkWin(bottomRight, BOARD_SIZE - 1, BOARD_SIZE - 1)).toBe(true);
  });
});

describe('placeStone', () => {
  it('원본 보드를 변경하지 않고 새 보드를 반환한다', () => {
    const board = createEmptyBoard();
    const next = placeStone(board, 3, 4, 'BLACK');

    expect(next[3][4]).toBe('BLACK');
    expect(board[3][4]).toBeNull();
    expect(next).not.toBe(board);
    expect(next[3]).not.toBe(board[3]); // 행까지 깊게 복사돼야 함
  });
});

describe('isBoardFull', () => {
  it('빈 보드는 false', () => {
    expect(isBoardFull(createEmptyBoard())).toBe(false);
  });

  it('한 칸이라도 비면 false', () => {
    const board = filledBoard();
    board[10][10] = null;
    expect(isBoardFull(board)).toBe(false);
  });

  it('모두 채워지면 true', () => {
    expect(isBoardFull(filledBoard())).toBe(true);
  });
});

describe('inBounds / opposite', () => {
  it('보드 밖 좌표를 걸러낸다', () => {
    expect(inBounds(0, 0)).toBe(true);
    expect(inBounds(BOARD_SIZE - 1, BOARD_SIZE - 1)).toBe(true);
    expect(inBounds(-1, 0)).toBe(false);
    expect(inBounds(0, BOARD_SIZE)).toBe(false);
  });

  it('색을 뒤집는다', () => {
    expect(opposite('BLACK')).toBe('WHITE');
    expect(opposite('WHITE')).toBe('BLACK');
  });
});
