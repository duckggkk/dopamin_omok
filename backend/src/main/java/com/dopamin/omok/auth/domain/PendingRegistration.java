package com.dopamin.omok.auth.domain;

import lombok.Getter;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 이메일 인증을 마치기 전의 "가입 대기" 상태.
 * <p>
 * 미인증 계정을 RDS(users)에 만들지 않고, 인증이 끝나는 바로 그 순간에만 실제 User 를 생성하기 위해
 * 가입 입력값(이메일·해시된 비밀번호·닉네임)과 6자리 인증코드를 함께 들고 Redis 에 TTL 로 보관한다.
 * (Redis 키는 email — 사용자당 1건, 재발송하면 덮어쓴다.)
 */
@Getter
public class PendingRegistration {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final String email;
    private final String encodedPassword;
    private final String nickname;
    private final String code;
    private final LocalDateTime expiresAt;
    private int failedAttempts;

    private PendingRegistration(String email, String encodedPassword, String nickname,
                                String code, LocalDateTime expiresAt, int failedAttempts) {
        this.email = email;
        this.encodedPassword = encodedPassword;
        this.nickname = nickname;
        this.code = code;
        this.expiresAt = expiresAt;
        this.failedAttempts = failedAttempts;
    }

    /** 신규 가입 대기 생성. validMinutes 분 뒤 만료. */
    public static PendingRegistration create(String email, String encodedPassword,
                                             String nickname, long validMinutes) {
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        return new PendingRegistration(
                email, encodedPassword, nickname, code,
                LocalDateTime.now().plusMinutes(validMinutes), 0);
    }

    /** Redis 에서 읽어온 값으로 도메인 객체를 복원한다. */
    public static PendingRegistration restore(String email, String encodedPassword, String nickname,
                                              String code, LocalDateTime expiresAt, int failedAttempts) {
        return new PendingRegistration(email, encodedPassword, nickname, code, expiresAt, failedAttempts);
    }

    /** 같은 가입 입력값으로 인증코드만 새로 발급(재발송용). 만료·시도횟수도 초기화된다. */
    public PendingRegistration reissue(long validMinutes) {
        return create(email, encodedPassword, nickname, validMinutes);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean matches(String code) {
        return this.code.equals(code);
    }

    public void recordFailedAttempt() {
        this.failedAttempts++;
    }

    public boolean isAttemptLimitExceeded() {
        return failedAttempts >= MAX_FAILED_ATTEMPTS;
    }
}
