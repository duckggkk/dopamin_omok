package com.dopamin.omok.global.security.jwt;

import com.dopamin.omok.user.domain.UserRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey; 

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(
            Long userId, UUID publicId, String email, String nickName, UserRole role, Long tokenVersion) {
        return buildToken(userId, publicId, email, nickName, role, tokenVersion, jwtProperties.getAccessTokenExpiration());
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    private String buildToken(
            Long userId, UUID publicId, String email, String nickName, UserRole role, Long tokenVersion, long expiration) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(JwtAuthConstants.CLAIM_PUBLIC_ID, publicId.toString())
                .claim(JwtAuthConstants.CLAIM_EMAIL, email)
                .claim(JwtAuthConstants.CLAIM_NICKNAME, nickName)
                .claim(JwtAuthConstants.CLAIM_ROLE, role.name())
                .claim(JwtAuthConstants.CLAIM_TOKEN_VERSION, tokenVersion)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Long extractTokenVersion(String token) {
        Object version = parseToken(token).get(JwtAuthConstants.CLAIM_TOKEN_VERSION);
        if (version instanceof Integer) return ((Integer) version).longValue();
        if (version instanceof Long) return (Long) version;
        return 0L;
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
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

    public UUID extractPublicId(String token) {
        return UUID.fromString(parseToken(token).get(JwtAuthConstants.CLAIM_PUBLIC_ID, String.class));
    }

    public String extractNickName(String token){
        return parseToken(token).get(JwtAuthConstants.CLAIM_NICKNAME, String.class);
    }

    public String extractEmail(String token) {
        return parseToken(token).get(JwtAuthConstants.CLAIM_EMAIL, String.class);
    }

    public UserRole extractRole(String token) {
        return UserRole.valueOf(parseToken(token).get(JwtAuthConstants.CLAIM_ROLE, String.class));
    }

    public long getRefreshTokenExpirationMillis() {
        return jwtProperties.getRefreshTokenExpiration();
    }
}
