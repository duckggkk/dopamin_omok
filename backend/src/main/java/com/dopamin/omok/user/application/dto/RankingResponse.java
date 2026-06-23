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
        int classicRating,
        int physicalRating,
        int winRate
) {
    /**
     * 선택한 모드의 전적(stats)으로 한 행을 만든다.
     * 레이팅은 두 모드 다 담아 프론트가 탭에 맞는 값을 보여줄 수 있게 한다.
     */
    public static RankingResponse of(int rank, User user, ModeStats stats) {
        return new RankingResponse(
                rank,
                user.getPublicId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                stats.wins(),
                stats.losses(),
                stats.draws(),
                stats.totalGames(),
                user.getClassicRating(),
                user.getPhysicalRating(),
                stats.winRate()
        );
    }
}
