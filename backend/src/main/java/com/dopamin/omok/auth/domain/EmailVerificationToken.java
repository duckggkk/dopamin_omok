package com.dopamin.omok.auth.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Entity
@Table(name = "email_verification_tokens", indexes = {
        @Index(name = "idx_evt_user_id", columnList = "user_id"),
        @Index(name = "idx_evt_token", columnList = "token")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private EmailVerificationToken(Long userId, String token, LocalDateTime expiresAt) {
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public static EmailVerificationToken create(Long userId, long validMinutes) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        return new EmailVerificationToken(
                userId,
                code,
                LocalDateTime.now().plusMinutes(validMinutes)
        );
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
