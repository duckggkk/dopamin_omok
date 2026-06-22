package com.dopamin.omok.auth.domain;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Refresh 토큰. Redis 에 저장되며 만료시각까지의 TTL 로 자동 소멸한다.
 * 사용자당 1건만 유지된다(재로그인/갱신 시 이전 토큰을 지우고 새로 발급).
 */
@Getter
public class RefreshToken {

    private final Long userId;
    private final String token;
    private final LocalDateTime expiresAt;

    private RefreshToken(Long userId, String token, LocalDateTime expiresAt) {
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken of(Long userId, String token, LocalDateTime expiresAt) {
        return new RefreshToken(userId, token, expiresAt);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}
