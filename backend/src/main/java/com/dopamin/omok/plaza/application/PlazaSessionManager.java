package com.dopamin.omok.plaza.application;

import com.dopamin.omok.plaza.application.dto.PlazaJoinResponse;
import com.dopamin.omok.plaza.application.dto.PlazaSnapshot;
import com.dopamin.omok.plaza.application.dto.PlazaSnapshot.PlazaPlayerView;
import com.dopamin.omok.plaza.application.port.out.PlazaEventPublisherPort;
import com.dopamin.omok.plaza.config.PlazaProperties;
import com.dopamin.omok.plaza.domain.Direction;
import com.dopamin.omok.plaza.domain.PlazaAppearance;
import com.dopamin.omok.plaza.domain.PlazaPlayer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 모든 광장 채널의 메모리 상태 + 틱 루프를 소유한다(비영속, 런타임 전용).
 * - 입력(여러 WS 스레드)과 틱(단일 스케줄러)은 채널별 락으로 직렬화된다.
 * - 매 틱 이동을 반영한 뒤 채널 스냅샷을 /topic/plaza/{channelId} 로 브로드캐스트한다.
 * - 좌표/외형은 DB에 저장하지 않는다(게임 규칙 없음 → 피지컬 오목보다 단순).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlazaSessionManager {

    private final PlazaProperties props;
    private final PlazaEventPublisherPort eventPublisher;

    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();
    /** disconnect 역추적용: sessionId → 어느 채널의 어느 유저인지. */
    private final ConcurrentHashMap<String, Member> sessionIndex = new ConcurrentHashMap<>();
    private final AtomicInteger channelCounter = new AtomicInteger(0);
    private final Object allocationLock = new Object();
    private ScheduledExecutorService scheduler;

    private static final class Channel {
        final String id;
        final ReentrantLock lock = new ReentrantLock();
        final Map<Long, PlazaPlayer> players = new LinkedHashMap<>();
        /** 직전 틱에 누군가 움직였는지 — 모두 멈춘 직후 '정지 프레임' 1장만 더 보내고 이후 틱은 건너뛰기 위함. */
        boolean movedLastTick = false;
        Channel(String id) { this.id = id; }
    }

    private record Member(String channelId, Long userId) {}

    @PostConstruct
    void startLoop() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "plaza-tick");
            t.setDaemon(true);
            return t;
        });
        long interval = props.tickIntervalMs();
        scheduler.scheduleAtFixedRate(this::tickSafely, interval, interval, TimeUnit.MILLISECONDS);
        log.info("광장 틱 루프 시작 (interval={}ms, capacity={})", interval, props.channelCapacity());
    }

    @PreDestroy
    void stopLoop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    // ===================== 채널 배정 =====================

    /**
     * 입장 가능한 채널 배정(채우기 우선: 정원 미달 채널 먼저, 없으면 새 채널 생성).
     * 실제 플레이어 추가/스폰은 WS join 에서 이뤄진다(여기선 채널만 고른다).
     */
    public PlazaJoinResponse allocate() {
        synchronized (allocationLock) {
            String channelId = null;
            for (Channel c : channels.values()) {
                c.lock.lock();
                try {
                    if (c.players.size() < props.channelCapacity()) {
                        channelId = c.id;
                        break;
                    }
                } finally {
                    c.lock.unlock();
                }
            }
            if (channelId == null) {
                channelId = "plaza-" + channelCounter.incrementAndGet();
                channels.putIfAbsent(channelId, new Channel(channelId));
            }
            return new PlazaJoinResponse(channelId, props.worldWidth(), props.worldHeight(), props.channelCapacity());
        }
    }

    // ===================== 생명주기 =====================

    public void join(String channelId, String sessionId, Long userId,
                     String publicId, String nickname, PlazaAppearance appearance) {
        Channel c = channels.computeIfAbsent(channelId, Channel::new);
        c.lock.lock();
        try {
            int margin = props.spawnMargin();
            int x = randomBetween(margin, props.worldWidth() - margin);
            int y = randomBetween(margin, props.worldHeight() - margin);
            c.players.put(userId, new PlazaPlayer(userId, publicId, nickname, x, y, appearance));
            if (sessionId != null) sessionIndex.put(sessionId, new Member(channelId, userId));
            broadcast(c);
            log.debug("광장 입장: channel={} user={} ({}명)", channelId, userId, c.players.size());
        } finally {
            c.lock.unlock();
        }
    }

    /** WS 입력 적용. 채널 미참가 userId 는 무시(서버 권위). */
    public void applyInput(String channelId, Long userId, PlazaInputType type, Direction direction) {
        Channel c = channels.get(channelId);
        if (c == null) return;
        c.lock.lock();
        try {
            PlazaPlayer player = c.players.get(userId);
            if (player == null) return;
            switch (type) {
                case MOVE_START -> player.startMove(direction);
                case MOVE_STOP -> player.stopMove();
            }
        } finally {
            c.lock.unlock();
        }
    }

    public void updateAppearance(String channelId, Long userId, PlazaAppearance appearance) {
        Channel c = channels.get(channelId);
        if (c == null) return;
        c.lock.lock();
        try {
            PlazaPlayer player = c.players.get(userId);
            if (player != null) {
                player.setAppearance(appearance);
                broadcast(c);
            }
        } finally {
            c.lock.unlock();
        }
    }

    public boolean isMember(String channelId, Long userId) {
        Channel c = channels.get(channelId);
        if (c == null) return false;
        c.lock.lock();
        try {
            return c.players.containsKey(userId);
        } finally {
            c.lock.unlock();
        }
    }

    /** 연결 끊김 시 호출(WebSocketEventListener). 광장 세션이 아니면 no-op. */
    public void handleDisconnect(String sessionId) {
        Member m = sessionIndex.remove(sessionId);
        if (m == null) return;
        leave(m.channelId(), m.userId());
    }

    public void leave(String channelId, Long userId) {
        Channel c = channels.get(channelId);
        if (c == null) return;
        c.lock.lock();
        try {
            if (c.players.remove(userId) != null) {
                broadcast(c);
                log.debug("광장 퇴장: channel={} user={} ({}명)", channelId, userId, c.players.size());
            }
        } finally {
            c.lock.unlock();
        }
        cleanupIfEmpty(c); // 빈 채널은 제거 → allocate 가 새로 만든다
    }

    private void cleanupIfEmpty(Channel c) {
        synchronized (allocationLock) {
            c.lock.lock();
            try {
                if (c.players.isEmpty()) channels.remove(c.id);
            } finally {
                c.lock.unlock();
            }
        }
    }

    // ===================== 틱 =====================

    private void tickSafely() {
        for (Channel c : channels.values()) {
            c.lock.lock();
            try {
                if (c.players.isEmpty()) continue;
                boolean moving = false;
                for (PlazaPlayer p : c.players.values()) {
                    if (p.isMoving()) {
                        p.step(props.moveStep(), props.worldWidth(), props.worldHeight());
                        moving = true;
                    }
                }
                // 아웃바운드 절감(정지 컷): 누군가 움직이는 동안만 매 틱 전송하고,
                // 모두 멈추면 '정지 프레임' 한 장만 더 보낸 뒤 완전 idle 인 틱은 건너뛴다.
                // (입장/퇴장/외형 변경은 그 즉시 별도로 broadcast 하므로 정지 중에도 갱신은 보장됨)
                if (moving || c.movedLastTick) broadcast(c);
                c.movedLastTick = moving;
            } catch (Exception e) {
                log.warn("광장 틱 오류 channel={}: {}", c.id, e.getMessage());
            } finally {
                c.lock.unlock();
            }
        }
    }

    // ===================== 브로드캐스트 =====================

    private void broadcast(Channel c) {
        eventPublisher.publishSnapshot(c.id, toSnapshot(c));
    }

    private PlazaSnapshot toSnapshot(Channel c) {
        List<PlazaPlayerView> views = new ArrayList<>(c.players.size());
        for (PlazaPlayer p : c.players.values()) {
            views.add(new PlazaPlayerView(
                    p.getPublicId(),
                    p.getNickname(),
                    p.getX(),
                    p.getY(),
                    p.getFacing().name(),
                    p.isMoving(),
                    p.getAppearance()
            ));
        }
        return new PlazaSnapshot(c.id, props.worldWidth(), props.worldHeight(), views, System.currentTimeMillis());
    }

    private static int randomBetween(int min, int max) {
        return max <= min ? min : ThreadLocalRandom.current().nextInt(min, max);
    }
}
