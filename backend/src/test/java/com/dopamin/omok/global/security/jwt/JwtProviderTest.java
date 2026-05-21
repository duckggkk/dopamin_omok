package com.dopamin.omok.global.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-for-jwt-testing-must-be-32-chars-long");
        props.setAccessTokenExpiration(3_600_000L);
        props.setRefreshTokenExpiration(604_800_000L);
        jwtProvider = new JwtProvider(props);
    }

    @Test
    @DisplayName("액세스 토큰 생성 후 검증 성공")
    void generateAccessToken_validToken() {
        String token = jwtProvider.generateAccessToken(1L, "test@test.com", "USER");

        assertThat(token).isNotBlank();
        assertThat(jwtProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("리프레시 토큰 생성 후 검증 성공")
    void generateRefreshToken_validToken() {
        String token = jwtProvider.generateRefreshToken(1L);

        assertThat(token).isNotBlank();
        assertThat(jwtProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("액세스 토큰에서 userId 추출")
    void extractUserId_fromAccessToken() {
        String token = jwtProvider.generateAccessToken(42L, "user@test.com", "USER");

        assertThat(jwtProvider.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("리프레시 토큰에서 userId 추출")
    void extractUserId_fromRefreshToken() {
        String token = jwtProvider.generateRefreshToken(7L);

        assertThat(jwtProvider.extractUserId(token)).isEqualTo(7L);
    }

    @Test
    @DisplayName("만료된 토큰은 검증 실패")
    void validateToken_expiredToken_false() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-for-jwt-testing-must-be-32-chars-long");
        props.setAccessTokenExpiration(-1000L);
        props.setRefreshTokenExpiration(-1000L);
        JwtProvider expiredProvider = new JwtProvider(props);

        String token = expiredProvider.generateAccessToken(1L, "test@test.com", "USER");

        assertThat(jwtProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("위조된 토큰은 검증 실패")
    void validateToken_tamperedToken_false() {
        String token = jwtProvider.generateAccessToken(1L, "test@test.com", "USER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(jwtProvider.validateToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("빈 문자열은 검증 실패")
    void validateToken_empty_false() {
        assertThat(jwtProvider.validateToken("")).isFalse();
        assertThat(jwtProvider.validateToken("   ")).isFalse();
    }

    @Test
    @DisplayName("getRefreshTokenExpirationMillis 반환값 정확")
    void getRefreshTokenExpirationMillis() {
        assertThat(jwtProvider.getRefreshTokenExpirationMillis()).isEqualTo(604_800_000L);
    }
}
