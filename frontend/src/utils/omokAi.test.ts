import { describe, it, expect } from 'vitest';
import { chooseAiMove, AI_LEVELS, MAX_AI_LEVEL } from './omokAi';
import { checkWin, placeStone } from './omokEngine';
import { createEmptyBoard } from '@/constants/board';
import { Board, StoneColor } from '@/types';

/**
 * AI 는 jitter(무작위 흔들기) 때문에 "정확히 이 좌표" 를 단언하면 불안정해진다.
 * 그래서 좌표 대신 결과의 성질("이 수를 두면 이긴다", "상대 5목 자리를 메운다")을 검증한다.
 * 무작위성이 0 인 5단계(jitter 4, vcfDepth 0)를 기준 난이도로 쓴다.
 */
const STABLE_LEVEL = 5;

const lineOf = (
  board: Board,
  row: number,
  startCol: number,
  count: number,
  color: StoneColor,
): void => {
  for (let i = 0; i < count; i++) board[row][startCol + i] = color;
};

describe('chooseAiMove', () => {
  it('한 수로 이길 수 있으면 그 수를 둔다', () => {
    const board = createEmptyBoard();
    lineOf(board, 7, 3, 4, 'BLACK'); // AI(흑) 4목 — (7,2) 또는 (7,7) 이면 5목

    const move = chooseAiMove(board, 'BLACK', STABLE_LEVEL);

    expect(move).not.toBeNull();
    const after = placeStone(board, move!.row, move!.col, 'BLACK');
    expect(checkWin(after, move!.row, move!.col)).toBe(true);
  });

  it('상대가 한 수로 이기는 자리를 막는다', () => {
    const board = createEmptyBoard();
    lineOf(board, 7, 3, 4, 'WHITE'); // 사람(백) 4목 — 열린 양끝은 (7,2)와 (7,7)

    const move = chooseAiMove(board, 'BLACK', STABLE_LEVEL);

    expect(move).not.toBeNull();
    expect([
      [7, 2],
      [7, 7],
    ]).toContainEqual([move!.row, move!.col]);
  });

  it('빈 보드에서도 유효한 좌표를 돌려준다', () => {
    const move = chooseAiMove(createEmptyBoard(), 'BLACK', STABLE_LEVEL);

    expect(move).not.toBeNull();
    expect(move!.row).toBeGreaterThanOrEqual(0);
    expect(move!.row).toBeLessThan(15);
    expect(move!.col).toBeGreaterThanOrEqual(0);
    expect(move!.col).toBeLessThan(15);
  });

  it('둘 곳이 없으면 null 을 돌려준다', () => {
    const full = createEmptyBoard().map((row) => row.map(() => 'BLACK' as StoneColor));
    expect(chooseAiMove(full, 'WHITE', STABLE_LEVEL)).toBeNull();
  });

  it('빈 칸에만 착수한다', () => {
    const board = createEmptyBoard();
    lineOf(board, 7, 3, 3, 'BLACK');
    lineOf(board, 8, 3, 3, 'WHITE');

    const move = chooseAiMove(board, 'BLACK', STABLE_LEVEL);

    expect(move).not.toBeNull();
    expect(board[move!.row][move!.col]).toBeNull();
  });

  it.each([0, -1, 99])('범위를 벗어난 level(%i) 도 가장 가까운 단계로 보정해 동작한다', (level) => {
    expect(chooseAiMove(createEmptyBoard(), 'BLACK', level)).not.toBeNull();
  });
});

describe('AI_LEVELS', () => {
  it('백엔드 AiProgressService.MAX_LEVEL(=9) 과 단계 수가 일치한다', () => {
    expect(AI_LEVELS).toHaveLength(9);
    expect(MAX_AI_LEVEL).toBe(9);
  });

  it('level 값이 1부터 빠짐없이 이어진다', () => {
    expect(AI_LEVELS.map((l) => l.level)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9]);
  });

  it('난이도 사다리가 단조롭다 — 위 단계가 더 잘 막고 덜 흔들린다', () => {
    for (let i = 1; i < AI_LEVELS.length; i++) {
      const prev = AI_LEVELS[i - 1];
      const cur = AI_LEVELS[i];

      expect(cur.defenseWeight).toBeGreaterThanOrEqual(prev.defenseWeight);
      expect(cur.jitter).toBeLessThanOrEqual(prev.jitter);
      expect(cur.topK).toBeGreaterThanOrEqual(prev.topK);
      expect(cur.vcfDepth).toBeGreaterThanOrEqual(prev.vcfDepth);
    }
  });

  it('표시 정보가 모두 채워져 있다', () => {
    for (const level of AI_LEVELS) {
      expect(level.label).not.toBe('');
      expect(level.emoji).not.toBe('');
      expect(level.desc).not.toBe('');
    }
  });
});
