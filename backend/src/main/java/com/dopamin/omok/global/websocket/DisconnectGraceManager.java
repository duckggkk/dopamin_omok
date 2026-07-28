package com.dopamin.omok.global.websocket;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class DisconnectGraceManager {

    private static final long GRACE_SECONDS = 30;
    private static final int MAX_CHANCES = 2;

    private final TransactionTemplate transactionTemplate;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    @Autowired
    public DisconnectGraceManager(TransactionTemplate transactionTemplate) {
        this(transactionTemplate, Executors.newScheduledThreadPool(4));
    }

    DisconnectGraceManager(TransactionTemplate transactionTemplate, ScheduledExecutorService scheduler) {
        this.transactionTemplate = transactionTemplate;
        this.scheduler = scheduler;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    /**
     * IN_PROGRESS 중 disconnect 시 호출.
     * MAX_CHANCES 초과 시 즉시 graceAction 실행, 이하이면 GRACE_SECONDS 후 실행.
     * 재접속(cancelGrace)하면 타이머 취소.
     */
    public void scheduleGrace(String roomCode, Long userId, Runnable graceAction) {
        String key = key(roomCode, userId);
        // computeIfAbsent는 key에 해당하는 값이 없으면 람다식으로 만들어라는뜻
        int count = counts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        //패배처리
        if (count > MAX_CHANCES) {
            log.debug("Grace: no more chances room={} user={}, immediate", roomCode, userId);
            runInTransaction(graceAction, roomCode, userId);
            counts.remove(key);
            return;
        }

        log.debug("Grace: {}/{} room={} user={}", count, MAX_CHANCES, roomCode, userId);
        cancelTimer(key); // 이벤트가 연달아 들어와 패배처리 두번 실행되는걸 방지
        ScheduledFuture<?> future = scheduler.schedule(() -> { // 실행 예약
            timers.remove(key); // 이 람다가 실행 = 패배처리 로직이니 타이머 초기화가 올바름
            log.debug("Grace: expired room={} user={}", roomCode, userId);
            runInTransaction(graceAction, roomCode, userId);
        }, GRACE_SECONDS, TimeUnit.SECONDS);// 인자가 command, 시간 값, 시간 기준
        timers.put(key, future);
    }

    /** 재접속 시 호출 — 대기 중인 타이머를 취소한다. */
    public boolean cancelGrace(String roomCode, Long userId) {
        return cancelTimer(key(roomCode, userId));
    }

    /**
     * 새 판이 시작되거나 방이 닫힐 때 호출 — 그 방의 유예 타이머와 끊김 횟수를 전부 제거한다.
     * <p>
     * {@code MAX_CHANCES} 는 "한 판에서 봐주는 횟수"이므로 새 판이 시작되면 초기화해야 한다.
     * 초기화 지점을 "게임 종료"가 아니라 "게임 시작"으로 잡은 이유는, 종료 경로가
     * 항복·시간초과·연결 끊김·방장 퇴장·리매치 등 여러 곳에 흩어져 있어 하나만 빠뜨려도
     * 카운트가 남는 반면, 시작 지점은 {@code startGame} 과 {@code startRematch} 둘뿐이라
     * 어떤 경로로 끝났든 다음 판은 항상 깨끗하게 시작되기 때문이다.
     * <p>
     * 두 맵 전체를 훑지만 항목 수가 '현재 유예 중인 사람 수'라 매우 작으므로
     * 방별 인덱스를 따로 두지 않는다.
     */
    public void clearRoom(String roomCode) {
        String prefix = roomCode + ":";
        // 맵에서 지우기만 하면 예약된 작업은 그대로 실행되므로 cancelTimer 로 취소해야 한다.
        // cancelTimer 가 timers 를 수정하므로 대상 키를 먼저 리스트로 복사해 둔다.
        timers.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .toList()
                .forEach(this::cancelTimer);
        counts.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private void runInTransaction(Runnable action, String roomCode, Long userId) {
        try {
            transactionTemplate.execute(status -> { //트랜젝션 열기
                action.run(); //액션실핼
                return null;
            });
        } catch (Exception e) {
            log.error("Grace action error room={} user={}", roomCode, userId, e); //예외 수동 로깅
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
