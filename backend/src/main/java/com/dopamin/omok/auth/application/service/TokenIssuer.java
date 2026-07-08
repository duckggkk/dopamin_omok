package com.dopamin.omok.auth.application.service;

import com.dopamin.omok.auth.application.dto.TokenResponse;
import com.dopamin.omok.auth.application.port.out.DeleteRefreshTokenPort;
import com.dopamin.omok.auth.application.port.out.SaveRefreshTokenPort;
import com.dopamin.omok.auth.domain.RefreshToken;
import com.dopamin.omok.global.security.jwt.JwtProvider;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 로그인 성공 시 access/refresh 토큰을 발급하는 공통 컴포넌트.
 *
 * <p>일반 로그인({@link AuthService})과 소셜 로그인({@link OAuth2Service})이 동일한 토큰 발급
 * 규칙(기존 refresh 토큰 폐기 후 새 토큰 1건 저장 = 단일 세션)을 공유하도록 한 곳으로 모았다.
 * 호출 전에 {@code user.incrementTokenVersion()} 으로 토큰 버전을 올려 다른 기기 세션을 무효화한다.
 */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtProvider jwtProvider;
    private final SaveRefreshTokenPort saveRefreshTokenPort;
    private final DeleteRefreshTokenPort deleteRefreshTokenPort;

    public TokenResponse issue(User user) {
        String accessToken = jwtProvider.generateAccessToken(
                user.getId(), user.getPublicId(), user.getEmail(), user.getNickname(), user.getRole(), user.getTokenVersion()
        );
        String refreshTokenValue = jwtProvider.generateRefreshToken(user.getId());

        // 단일 세션: 기존 refresh 토큰을 지우고 새 토큰만 남긴다.
        deleteRefreshTokenPort.deleteByUserId(user.getId());

        long expirationMillis = jwtProvider.getRefreshTokenExpirationMillis();
        RefreshToken refreshToken = RefreshToken.of(
                user.getId(),
                refreshTokenValue,
                LocalDateTime.now().plusNanos(expirationMillis * 1_000_000)
        );
        saveRefreshTokenPort.save(refreshToken);

        return TokenResponse.of(accessToken, refreshTokenValue, expirationMillis);
    }
}
