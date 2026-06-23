import { StatMode, ModeStats } from '@/types';

const ZERO: ModeStats = { wins: 0, losses: 0, draws: 0, totalGames: 0, winRate: 0 };

/** 통합 전적을 wins/losses/draws 에서 만든다(승률 계산 포함). */
export const totalStats = (wins: number, losses: number, draws: number): ModeStats => {
  const totalGames = wins + losses + draws;
  return {
    wins,
    losses,
    draws,
    totalGames,
    winRate: totalGames > 0 ? Math.round((wins / totalGames) * 100) : 0,
  };
};

/**
 * 선택한 탭의 전적을 고른다.
 * 구버전 캐시로 classic/physical 이 비어 있으면 0으로 안전 처리한다.
 */
export const pickStats = (
  mode: StatMode,
  total: ModeStats,
  classic?: ModeStats | null,
  physical?: ModeStats | null,
): ModeStats => {
  if (mode === 'CLASSIC') return classic ?? ZERO;
  if (mode === 'PHYSICAL') return physical ?? ZERO;
  return total;
};
