package com.dopamin.omok.global.security.jwt;

import com.dopamin.omok.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "test-secret-key-for-jwt-testing-must-be-at-least-32-bytes-long",
                3_600_000L,
                604_800_000L
        );
        jwtProvider = new JwtProvider(properties);
    }

    @Test
    @DisplayName("액세스 토큰 생성 후 클레임을 다시 추출")
    void generateAndParseAccessToken() {
        UUID publicId = UUID.randomUUID();
        String token = jwtProvider.generateAccessToken(42L, publicId, "user@x.com", "nickname", UserRole.USER, 3L);

        assertThat(jwtProvider.validateToken(token)).isTrue();
        assertThat(jwtProvider.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtProvider.extractPublicId(token)).isEqualTo(publicId);
        assertThat(jwtProvider.extractEmail(token)).isEqualTo("user@x.com");
        assertThat(jwtProvider.extractNickName(token)).isEqualTo("nickname");
        assertThat(jwtProvider.extractRole(token)).isEqualTo(UserRole.USER);
        assertThat(jwtProvider.extractTokenVersion(token)).isEqualTo(3L);
    }

    @Test
    @DisplayName("리프레시 토큰의 subject 는 userId")
    void refreshTokenSubject() {
        String token = jwtProvider.generateRefreshToken(7L);
        assertThat(jwtProvider.validateToken(token)).isTrue();
        assertThat(jwtProvider.extractUserId(token)).isEqualTo(7L);
    }

    @Test
    @DisplayName("형식이 잘못된 토큰은 검증 실패")
    void invalidTokenFailsValidation() {
        assertThat(jwtProvider.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    @DisplayName("변조된 토큰은 서명 검증 실패")
    void tamperedTokenFailsValidation() {
        String token = jwtProvider.generateAccessToken(
                1L, UUID.randomUUID(), "user@x.com", "nickname", UserRole.USER, 0L);
        assertThat(jwtProvider.validateToken(token + "tampered")).isFalse();
    }
}
