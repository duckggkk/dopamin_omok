package com.dopamin.omok.user.application.dto;

/**
 * 한 모드(통합/일반/피지컬)의 전적. winRate(승률 %)까지 서버에서 계산해 내려준다.
 */
public record ModeStats(
        int wins,
        int losses,
        int draws,
        int totalGames,
        int winRate
) {
    public static ModeStats of(int wins, int losses, int draws) {
        int total = wins + losses + draws;
        int winRate = total > 0 ? Math.round(wins * 100f / total) : 0;
        return new ModeStats(wins, losses, draws, total, winRate);
    }
}
