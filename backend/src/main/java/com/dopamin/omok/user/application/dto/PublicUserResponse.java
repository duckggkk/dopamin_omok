package com.dopamin.omok.user.application.dto;

import com.dopamin.omok.user.domain.User;

import java.time.LocalDateTime;
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
        boolean profilePrivate,
        LocalDateTime createdAt
) {
    public static PublicUserResponse from(User user) {
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
                user.isProfilePrivate(),
                user.getCreatedAt()
        );
    }
}
