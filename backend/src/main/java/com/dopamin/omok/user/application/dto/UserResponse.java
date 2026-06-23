package com.dopamin.omok.user.application.dto;

import com.dopamin.omok.user.domain.AuthProvider;
import com.dopamin.omok.user.domain.User;

import java.time.LocalDateTime;
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
        ModeStats classic,     // 일반 오목 전적(게임 기록 집계)
        ModeStats physical,    // 피지컬 오목 전적(게임 기록 집계)
        Integer currency,
        boolean profilePrivate,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user, ModeStats classic, ModeStats physical) {
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
                user.getCurrency() != null ? user.getCurrency() : 0,
                user.isProfilePrivate(),
                user.getCreatedAt()
        );
    }
}
