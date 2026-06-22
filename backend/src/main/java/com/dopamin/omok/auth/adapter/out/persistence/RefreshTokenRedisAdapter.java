package com.dopamin.omok.auth.adapter.out.persistence;

import com.dopamin.omok.auth.application.port.out.DeleteRefreshTokenPort;
import com.dopamin.omok.auth.application.port.out.LoadRefreshTokenPort;
import com.dopamin.omok.auth.application.port.out.SaveRefreshTokenPort;
import com.dopamin.omok.auth.domain.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Refresh 토큰을 Redis 에 저장한다. 두 가지 조회가 필요해 키를 두 개 둔다.
 *   refresh:token:{token}  → {userId, expiresAt}   (토큰 문자열로 조회 — 토큰 갱신 시)
 *   refresh:user:{userId}  → token                 (userId 로 조회/삭제 — 로그인·로그아웃 시)
 * 사용자당 1건만 유지되며(재발급 전 deleteByUserId 호출), 둘 다 만료시각까지의 TTL 로 자동 소멸한다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenRedisAdapter
        implements LoadRefreshTokenPort, SaveRefreshTokenPort, DeleteRefreshTokenPort {

    private static final String TOKEN_PREFIX = "refresh:token:";
    private static final String USER_PREFIX = "refresh:user:";

    private final StringRedisTemplate redis;

    private String tokenKey(String token) {
        return TOKEN_PREFIX + token;
    }

    private String userKey(Long userId) {
        return USER_PREFIX + userId;
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        long seconds = Duration.between(LocalDateTime.now(), refreshToken.getExpiresAt()).getSeconds();
        Duration ttl = Duration.ofSeconds(Math.max(1, seconds));

        String tKey = tokenKey(refreshToken.getToken());
        redis.opsForHash().putAll(tKey, Map.of(
                "userId", String.valueOf(refreshToken.getUserId()),
                "expiresAt", refreshToken.getExpiresAt().toString()
        ));
        redis.expire(tKey, ttl);

        redis.opsForValue().set(userKey(refreshToken.getUserId()), refreshToken.getToken(), ttl);
        return refreshToken;
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        Map<Object, Object> entries = redis.opsForHash().entries(tokenKey(token));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(RefreshToken.of(
                Long.valueOf((String) entries.get("userId")),
                token,
                LocalDateTime.parse((String) entries.get("expiresAt"))
        ));
    }

    @Override
    public void delete(RefreshToken refreshToken) {
        redis.delete(tokenKey(refreshToken.getToken()));
        redis.delete(userKey(refreshToken.getUserId()));
    }

    @Override
    public void deleteByUserId(Long userId) {
        String token = redis.opsForValue().get(userKey(userId));
        if (token != null) {
            redis.delete(tokenKey(token));
        }
        redis.delete(userKey(userId));
    }
}
