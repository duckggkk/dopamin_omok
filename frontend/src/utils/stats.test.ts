import { describe, it, expect } from 'vitest';
import { totalStats, pickStats } from './stats';
import { ModeStats } from '@/types';

const stats = (wins: number, losses: number, draws: number): ModeStats =>
  totalStats(wins, losses, draws);

describe('totalStats', () => {
  it('승/패/무를 합쳐 총 판수를 낸다', () => {
    expect(totalStats(3, 2, 1)).toMatchObject({ wins: 3, losses: 2, draws: 1, totalGames: 6 });
  });

  it('승률을 정수 퍼센트로 반올림한다', () => {
    expect(totalStats(1, 2, 0).winRate).toBe(33); // 33.33... → 33
    expect(totalStats(2, 1, 0).winRate).toBe(67); // 66.67... → 67
    expect(totalStats(1, 1, 0).winRate).toBe(50);
  });

  it('무승부도 총 판수에 포함해 승률을 계산한다', () => {
    expect(totalStats(1, 1, 2).winRate).toBe(25); // 1 / 4
  });

  it('전적이 없으면 0으로 나누지 않고 승률 0을 돌려준다', () => {
    expect(totalStats(0, 0, 0)).toEqual({
      wins: 0,
      losses: 0,
      draws: 0,
      totalGames: 0,
      winRate: 0,
    });
  });

  it('전승·전패는 100 / 0', () => {
    expect(totalStats(5, 0, 0).winRate).toBe(100);
    expect(totalStats(0, 5, 0).winRate).toBe(0);
  });
});

describe('pickStats', () => {
  const total = stats(10, 5, 1);
  const classic = stats(6, 3, 1);
  const physical = stats(4, 2, 0);

  it('탭에 해당하는 전적을 고른다', () => {
    expect(pickStats('TOTAL', total, classic, physical)).toBe(total);
    expect(pickStats('CLASSIC', total, classic, physical)).toBe(classic);
    expect(pickStats('PHYSICAL', total, classic, physical)).toBe(physical);
  });

  it('구버전 캐시로 모드별 전적이 비어 있으면 0으로 안전 처리한다', () => {
    const zero = { wins: 0, losses: 0, draws: 0, totalGames: 0, winRate: 0 };

    expect(pickStats('CLASSIC', total, null, physical)).toEqual(zero);
    expect(pickStats('PHYSICAL', total, classic, undefined)).toEqual(zero);
  });

  it('모드별 전적이 없어도 통합 탭은 영향받지 않는다', () => {
    expect(pickStats('TOTAL', total, null, null)).toBe(total);
  });
});
