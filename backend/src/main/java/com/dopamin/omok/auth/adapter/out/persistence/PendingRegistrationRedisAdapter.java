package com.dopamin.omok.auth.adapter.out.persistence;

import com.dopamin.omok.auth.application.port.out.DeletePendingRegistrationPort;
import com.dopamin.omok.auth.application.port.out.LoadPendingRegistrationPort;
import com.dopamin.omok.auth.application.port.out.SavePendingRegistrationPort;
import com.dopamin.omok.auth.domain.PendingRegistration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 가입 대기(이메일 인증 전 상태)를 Redis 에 저장한다. 두 종류의 키를 쓴다.
 * <pre>
 *   signup:pending:{email}  (해시) → {password, nickname, code, expiresAt, failedAttempts}
 *                                    가입 본문. 사용자당 1건(재발송하면 덮어씀).
 *   signup:nick:{nickname}  (문자열) → email
 *                                    "이 닉네임을 인증 진행 중인 사람이 선점했다"는 예약(reservation).
 *                                    DB UNIQUE 가 최종 방어선이지만, 인증 도중 같은 닉네임을 또 선점하지
 *                                    못하게 가입 단계에서 미리 막아준다.
 * </pre>
 * 두 키 모두 (만료시각까지 남은 시간) + GRACE 의 TTL 로 자동 소멸한다. → 별도 청소 배치가 필요 없다.
 * GRACE 동안은 만료 직후라도 키가 잠깐 남아 "만료됨"을 정확히 응답하고, 그 뒤 Redis 가 알아서 지운다.
 */
@Component
@RequiredArgsConstructor
public class PendingRegistrationRedisAdapter
        implements SavePendingRegistrationPort, LoadPendingRegistrationPort, DeletePendingRegistrationPort {

    private static final String PENDING_PREFIX = "signup:pending:";
    private static final String NICK_PREFIX = "signup:nick:";
    private static final long GRACE_SECONDS = 60;

    private final StringRedisTemplate redis;

    private String pendingKey(String email) {
        return PENDING_PREFIX + email;
    }

    private String nickKey(String nickname) {
        return NICK_PREFIX + nickname;
    }

    @Override
    public PendingRegistration save(PendingRegistration pending) {
        String pKey = pendingKey(pending.getEmail());
        redis.opsForHash().putAll(pKey, Map.of(
                "password", pending.getEncodedPassword(),
                "nickname", pending.getNickname(),
                "code", pending.getCode(),
                "expiresAt", pending.getExpiresAt().toString(),
                "failedAttempts", String.valueOf(pending.getFailedAttempts())
        ));
        // 만료시각은 고정이므로 저장할 때마다 다시 계산해도 TTL 은 줄기만 할 뿐 늘어나지 않는다.
        long ttlSeconds = Duration.between(LocalDateTime.now(), pending.getExpiresAt()).getSeconds() + GRACE_SECONDS;
        Duration ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
        redis.expire(pKey, ttl);
        // 닉네임 예약을 가입 본문과 같은 TTL 로 맞춘다(가입 본문이 사라지면 예약도 곧 사라지게).
        redis.opsForValue().set(nickKey(pending.getNickname()), pending.getEmail(), ttl);
        return pending;
    }

    @Override
    public Optional<PendingRegistration> findByEmail(String email) {
        Map<Object, Object> entries = redis.opsForHash().entries(pendingKey(email));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(PendingRegistration.restore(
                email,
                (String) entries.get("password"),
                (String) entries.get("nickname"),
                (String) entries.get("code"),
                LocalDateTime.parse((String) entries.get("expiresAt")),
                Integer.parseInt((String) entries.get("failedAttempts"))
        ));
    }

    @Override
    public boolean isNicknameReserved(String nickname) {
        return Boolean.TRUE.equals(redis.hasKey(nickKey(nickname)));
    }

    @Override
    public void deleteByEmail(String email) {
        // 닉네임 예약 키를 같이 지우려면 닉네임을 알아야 하므로 가입 본문에서 먼저 읽는다.
        Object nickname = redis.opsForHash().get(pendingKey(email), "nickname");
        redis.delete(pendingKey(email));
        if (nickname != null) {
            redis.delete(nickKey((String) nickname));
        }
    }
}
