package com.dopamin.omok.auth.domain;

import lombok.Getter;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 이메일 인증코드(6자리). Redis 에 사용자당 1건만 저장되며 TTL 로 자동 만료된다.
 * (재발송하면 같은 키를 덮어쓴다 — 키는 userId)
 */
@Getter
public class EmailVerificationToken {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final Long userId;
    private final String token;
    private final LocalDateTime expiresAt;
    private int failedAttempts;

    private EmailVerificationToken(Long userId, String token, LocalDateTime expiresAt, int failedAttempts) {
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.failedAttempts = failedAttempts;
    }

    /** 신규 인증코드 생성. validMinutes 분 뒤 만료. */
    public static EmailVerificationToken create(Long userId, long validMinutes) {
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        return new EmailVerificationToken(
                userId,
                code,
                LocalDateTime.now().plusMinutes(validMinutes),
                0
        );
    }

    /** Redis 에서 읽어온 값으로 도메인 객체를 복원한다. */
    public static EmailVerificationToken restore(Long userId, String token,
                                                 LocalDateTime expiresAt, int failedAttempts) {
        return new EmailVerificationToken(userId, token, expiresAt, failedAttempts);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void recordFailedAttempt() {
        this.failedAttempts++;
    }

    public boolean isAttemptLimitExceeded() {
        return failedAttempts >= MAX_FAILED_ATTEMPTS;
    }
}
