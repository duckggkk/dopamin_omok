package com.dopamin.omok.global.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class DisconnectGraceManager {

    private static final long GRACE_SECONDS = 30;
    private static final int MAX_CHANCES = 2;

    private final TransactionTemplate transactionTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    /**
     * IN_PROGRESS 중 disconnect 시 호출.
     * MAX_CHANCES 초과 시 즉시 graceAction 실행, 이하이면 GRACE_SECONDS 후 실행.
     * 재접속(cancelGrace)하면 타이머 취소.
     */
    public void scheduleGrace(String roomCode, Long userId, Runnable graceAction) {
        String key = key(roomCode, userId);
        int count = counts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        if (count > MAX_CHANCES) {
            log.debug("Grace: no more chances room={} user={}, immediate", roomCode, userId);
            runInTransaction(graceAction, roomCode, userId);
            counts.remove(key);
            return;
        }

        log.debug("Grace: {}/{} room={} user={}", count, MAX_CHANCES, roomCode, userId);
        cancelTimer(key);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            timers.remove(key);
            log.debug("Grace: expired room={} user={}", roomCode, userId);
            runInTransaction(graceAction, roomCode, userId);
        }, GRACE_SECONDS, TimeUnit.SECONDS);
        timers.put(key, future);
    }

    /** 재접속 시 호출 — 대기 중인 타이머를 취소한다. */
    public boolean cancelGrace(String roomCode, Long userId) {
        return cancelTimer(key(roomCode, userId));
    }

    /** 방이 정상 종료될 때(항복, 리매치 등) 호출 — 타이머 + 카운트 모두 제거. */
    public void clearState(String roomCode, Long userId) {
        String key = key(roomCode, userId);
        cancelTimer(key);
        counts.remove(key);
    }

    private void runInTransaction(Runnable action, String roomCode, Long userId) {
        try {
            transactionTemplate.execute(status -> {
                action.run();
                return null;
            });
        } catch (Exception e) {
            log.error("Grace action error room={} user={}", roomCode, userId, e);
        }
    }

    private boolean cancelTimer(String key) {
        ScheduledFuture<?> f = timers.remove(key);
        if (f != null && f.cancel(false)) {
            log.debug("Grace: timer cancelled key={}", key);
            return true;
        }
        return false;
    }

    private String key(String roomCode, Long userId) {
        return roomCode + ":" + userId;
    }
}
