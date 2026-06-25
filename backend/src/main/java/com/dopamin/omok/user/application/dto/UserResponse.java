package com.dopamin.omok.user.application.dto;

import com.dopamin.omok.user.domain.AuthProvider;
import com.dopamin.omok.user.domain.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String nickname,
        String profileImageUrl,
        AuthProvider provider,
        Integer wins,
        Integer losses,
        Integer draws,
        Integer totalGames,
        Integer classicRating,
        Integer physicalRating,
        ModeStats classic,     // 일반 오목 랭크 전적(게임 기록 집계)
        ModeStats physical,    // 피지컬 오목 랭크 전적(게임 기록 집계)
        ModeStats casual,      // 캐주얼(일반) 전적 — 랭크와 분리 집계(레이팅 무관)
        Integer currency,
        List<TitleResponse> titles, // 획득한 칭호(업적 파생) — 첫 항목이 대표 칭호
        boolean profilePrivate,
        boolean guest,             // 비회원(게스트) 계정 여부 — 프론트가 멤버 전용 UI를 가리는 데 쓴다
        LocalDateTime createdAt
) {
    public static UserResponse from(User user, ModeStats classic, ModeStats physical, ModeStats casual) {
        return new UserResponse(
                user.getPublicId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider(),
                user.getWins(),
                user.getLosses(),
                user.getDraws(),
                user.getTotalGames(),
                user.getClassicRating(),
                user.getPhysicalRating(),
                classic,
                physical,
                casual,
                user.getCurrency() != null ? user.getCurrency() : 0,
                TitleResponse.earnedBy(user),
                user.isProfilePrivate(),
                user.isGuest(),
                user.getCreatedAt()
        );
    }
}
