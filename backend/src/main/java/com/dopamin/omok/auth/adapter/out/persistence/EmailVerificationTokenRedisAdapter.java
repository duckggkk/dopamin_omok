package com.dopamin.omok.auth.adapter.out.persistence;

import com.dopamin.omok.auth.application.port.out.DeleteEmailVerificationTokenPort;
import com.dopamin.omok.auth.application.port.out.LoadEmailVerificationTokenPort;
import com.dopamin.omok.auth.application.port.out.SaveEmailVerificationTokenPort;
import com.dopamin.omok.auth.domain.EmailVerificationToken;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 이메일 인증코드를 Redis 해시에 저장한다.
 *   키 : email_verify:{userId}   — 사용자당 1건(재발송하면 덮어씀)
 *   TTL: (만료시각까지 남은 시간) + GRACE.
 *        만료 직후 잠깐은 키가 남아 "만료됨" 에러를 정확히 돌려주고, GRACE 가 지나면 Redis 가 자동 삭제한다.
 *        → 별도의 청소 배치가 필요 없다.
 */
@Component
@RequiredArgsConstructor
public class EmailVerificationTokenRedisAdapter
        implements SaveEmailVerificationTokenPort, LoadEmailVerificationTokenPort, DeleteEmailVerificationTokenPort {

    private static final String KEY_PREFIX = "email_verify:";
    private static final long GRACE_SECONDS = 60;

    private final StringRedisTemplate redis;

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    @Override
    public EmailVerificationToken save(EmailVerificationToken token) {
        String key = key(token.getUserId());
        redis.opsForHash().putAll(key, Map.of(
                "token", token.getToken(),
                "expiresAt", token.getExpiresAt().toString(),
                "failedAttempts", String.valueOf(token.getFailedAttempts())
        ));
        // 만료시각은 고정이므로, 저장할 때마다 다시 계산해도 TTL 은 줄기만 할 뿐 늘어나지 않는다.
        long ttlSeconds = Duration.between(LocalDateTime.now(), token.getExpiresAt()).getSeconds() + GRACE_SECONDS;
        redis.expire(key, Duration.ofSeconds(Math.max(1, ttlSeconds)));
        return token;
    }

    @Override
    public Optional<EmailVerificationToken> findByUserIdAndToken(Long userId, String token) {
        return read(userId).filter(t -> t.getToken().equals(token));
    }

    @Override
    public Optional<EmailVerificationToken> findLatestByUserId(Long userId) {
        return read(userId);
    }

    @Override
    public void delete(EmailVerificationToken token) {
        redis.delete(key(token.getUserId()));
    }

    @Override
    public void deleteByUserId(Long userId) {
        redis.delete(key(userId));
    }

    private Optional<EmailVerificationToken> read(Long userId) {
        Map<Object, Object> entries = redis.opsForHash().entries(key(userId));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(EmailVerificationToken.restore(
                userId,
                (String) entries.get("token"),
                LocalDateTime.parse((String) entries.get("expiresAt")),
                Integer.parseInt((String) entries.get("failedAttempts"))
        ));
    }
}
