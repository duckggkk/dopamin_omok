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
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
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
                user.getCreatedAt()
        );
    }
}
