package com.dopamin.omok.user.application.dto;

import com.dopamin.omok.user.domain.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PublicUserResponse(
        UUID id,
        String nickname,
        String profileImageUrl,
        Integer wins,
        Integer losses,
        Integer draws,
        Integer totalGames,
        Integer classicRating,
        Integer physicalRating,
        ModeStats classic,     // 일반 오목 전적(게임 기록 집계)
        ModeStats physical,    // 피지컬 오목 전적(게임 기록 집계)
        List<TitleResponse> titles, // 획득한 칭호(업적 파생) — 첫 항목이 대표 칭호
        boolean profilePrivate,
        LocalDateTime createdAt
) {
    /**
     * 모드별 전적이 필요 없는 곳(게임/방 응답의 플레이어 등)에서 쓰는 간이 버전.
     * classic/physical 은 null 로 두어 불필요한 집계 쿼리를 피한다.
     */
    public static PublicUserResponse from(User user) {
        return from(user, null, null);
    }

    public static PublicUserResponse from(User user, ModeStats classic, ModeStats physical) {
        return new PublicUserResponse(
                user.getPublicId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getWins(),
                user.getLosses(),
                user.getDraws(),
                user.getTotalGames(),
                user.getClassicRating(),
                user.getPhysicalRating(),
                classic,
                physical,
                TitleResponse.earnedBy(user),
                user.isProfilePrivate(),
                user.getCreatedAt()
        );
    }
}
