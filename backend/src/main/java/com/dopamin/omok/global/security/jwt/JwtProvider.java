package com.dopamin.omok.global.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtProperties jwtProperties;

    // SecretKey를 한 번만 생성해 재사용 (thread-safe)
    private volatile SecretKey cachedKey;

    private SecretKey getSigningKey() {
        if (cachedKey == null) {
            synchronized (this) {
                if (cachedKey == null) {
                    // 시크릿 문자열을 UTF-8 바이트로 변환해 HMAC-SHA256 키 생성
                    // (이중 Base64 인코딩 버그 제거)
                    byte[] keyBytes = jwtProperties.getSecret()
                            .getBytes(StandardCharsets.UTF_8);
                    cachedKey = Keys.hmacShaKeyFor(keyBytes);
                }
            }
        }
        return cachedKey;
    }

    public String generateAccessToken(Long userId, String email, String role, Long tokenVersion) {
        return buildToken(userId, email, role, tokenVersion, jwtProperties.getAccessTokenExpiration());
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    private String buildToken(Long userId, String email, String role, Long tokenVersion, long expiration) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .claim("tokenVersion", tokenVersion)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public Long extractTokenVersion(String token) {
        Object version = parseToken(token).get("tokenVersion");
        if (version instanceof Integer) return ((Integer) version).longValue();
        if (version instanceof Long) return (Long) version;
        return 0L;
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException | MalformedJwtException e) {
            log.debug("JWT invalid format: {}", e.getMessage());
        } catch (io.jsonwebtoken.security.SignatureException | SecurityException e) {
            log.debug("JWT signature invalid: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("JWT empty: {}", e.getMessage());
        }
        return false;
    }

    public Long extractUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    public long getRefreshTokenExpirationMillis() {
        return jwtProperties.getRefreshTokenExpiration();
    }
}
