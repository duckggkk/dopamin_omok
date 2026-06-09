package com.dopamin.omok.user.application.dto;

import com.dopamin.omok.user.domain.User;

import java.util.UUID;

/**
 * 랭킹 1행. rank는 서버에서 순위(1-based)로 매겨 내려준다.
 */
public record RankingResponse(
        int rank,
        UUID userId,
        String nickname,
        String profileImageUrl,
        int wins,
        int losses,
        int draws,
        int totalGames,
        int winRate
) {
    public static RankingResponse of(int rank, User user) {
        int total = user.getTotalGames();
        int winRate = total > 0 ? Math.round(user.getWins() * 100f / total) : 0;
        return new RankingResponse(
                rank,
                user.getPublicId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getWins(),
                user.getLosses(),
                user.getDraws(),
                total,
                winRate
        );
    }
}
