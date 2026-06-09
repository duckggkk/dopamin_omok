package com.dopamin.omok.game.physical.application;

import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.physical.application.dto.PhysicalSnapshot;
import com.dopamin.omok.game.physical.application.dto.PhysicalSnapshot.ItemDropView;
import com.dopamin.omok.game.physical.application.dto.PhysicalSnapshot.PhysicalPlayerView;
import com.dopamin.omok.game.physical.config.PhysicalOmokProperties;
import com.dopamin.omok.game.physical.domain.Direction;
import com.dopamin.omok.game.physical.domain.ItemDrop;
import com.dopamin.omok.game.physical.domain.PhysicalGame;
import com.dopamin.omok.game.physical.domain.PhysicalOmokEngine;
import com.dopamin.omok.game.physical.domain.PhysicalPlayer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 진행 중인 모든 피지컬 오목 세션의 메모리 상태 + 게임 루프를 소유한다.
 * - 입력(여러 WS 스레드)과 틱(단일 스케줄러 스레드)은 세션별 락으로 직렬화된다.
 * - 매 틱(이동/스폰)과 입력 직후 전체 스냅샷을 브로드캐스트한다.
 * - 승자 확정 종료(5목/기권)는 {@link PhysicalGameEndedEvent} 로 발행 → RoomService 가 영속 처리(순환 의존 없음).
 * 영속/트랜잭션 코드는 이 클래스에 두지 않는다(런타임 전용).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhysicalGameSessionManager {

    private final PhysicalOmokEngine engine;
    private final PhysicalOmokProperties props;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    private static final class Session {
        final PhysicalGame game;
        final ReentrantLock lock = new ReentrantLock();
        Session(PhysicalGame game) {
            this.game = game;
        }
    }

    @PostConstruct
    void startLoop() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "physical-omok-tick");
            t.setDaemon(true);
            return t;
        });
        long interval = props.tickIntervalMs();
        scheduler.scheduleAtFixedRate(this::tickSafely, interval, interval, TimeUnit.MILLISECONDS);
        log.info("피지컬 오목 틱 루프 시작 (interval={}ms)", interval);
    }

    @PreDestroy
    void stopLoop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    // ===================== 생명주기 =====================

    public void register(PhysicalGame game) {
        sessions.put(game.roomCode(), new Session(game));
        broadcast(game);
        log.debug("피지컬 세션 등록: room={}", game.roomCode());
    }

    /** 강제 정리(연결 끊김/방장 퇴장). 승자 없이 종료 표시 후 최종 스냅샷만 보낸다(이벤트 미발행). */
    public void stopSession(String roomCode) {
        Session s = sessions.remove(roomCode);
        if (s == null) return;
        s.lock.lock();
        try {
            if (s.game.isInProgress()) s.game.finish(null);
            broadcast(s.game);
        } finally {
            s.lock.unlock();
        }
        log.debug("피지컬 세션 정리: room={}", roomCode);
    }

    // ===================== 입력 =====================

    /** WS 입력 적용. 비참가자/관전자(세션에 없는 userId)는 무시한다(서버 권위). */
    public void applyInput(String roomCode, Long userId, PhysicalInputType type, Direction direction) {
        Session s = sessions.get(roomCode);
        if (s == null) return;
        s.lock.lock();
        try {
            PhysicalGame game = s.game;
            if (!game.isInProgress()) return;
            PhysicalPlayer player = game.playerOf(userId);
            if (player == null) return; // 참가자 아님 → 무시

            long now = System.currentTimeMillis();
            switch (type) {
                case MOVE_START -> engine.startMove(game, player, direction, now);
                case MOVE_STOP -> engine.stopMove(player);
                case PLACE -> engine.place(game, player, now); // 승리는 settle 경과 후 tick에서 확정
                case DESTROY -> engine.destroy(game, player, now);
                case USE_ITEM -> engine.useItem(game, player, now);
            }
            broadcast(game); // 입력 직후 즉시 동기화(반응성)
        } finally {
            s.lock.unlock();
        }
    }

    /** 기권: 상대를 승자로 종료한다(승자 확정 → 이벤트 발행). */
    public void surrender(String roomCode, Long userId) {
        Session s = sessions.get(roomCode);
        if (s == null) return;
        Long winnerUserId = null;
        s.lock.lock();
        try {
            PhysicalGame game = s.game;
            if (!game.isInProgress()) return;
            PhysicalPlayer player = game.playerOf(userId);
            if (player == null) return;
            PhysicalPlayer opponent = game.opponentOf(player);
            if (opponent == null) return;
            game.finish(opponent.getColor());
            winnerUserId = game.winnerUserId();
            broadcast(game);
        } finally {
            s.lock.unlock();
        }
        if (winnerUserId != null) concludeWin(roomCode, winnerUserId);
    }

    /** 세션 제거 + 승자 확정 이벤트 발행(락 밖에서 — DB I/O가 세션 락을 잡지 않게 함). */
    private void concludeWin(String roomCode, Long winnerUserId) {
        sessions.remove(roomCode);
        eventPublisher.publishEvent(new PhysicalGameEndedEvent(roomCode, winnerUserId));
        log.debug("피지컬 세션 종료(승자 확정): room={} winnerUserId={}", roomCode, winnerUserId);
    }

    // ===================== 틱 루프 =====================

    private void tickSafely() {
        long now = System.currentTimeMillis();
        for (Session s : sessions.values()) {
            Long winnerUserId = null;
            s.lock.lock();
            try {
                PhysicalGame game = s.game;
                if (!game.isInProgress()) continue;
                engine.tickMovement(game, now);
                engine.maybeSpawnItem(game, now);
                engine.settlePendingWin(game, now); // 5목이 settle 동안 유지됐으면 여기서 승리 확정
                broadcast(game);
                if (!game.isInProgress()) winnerUserId = game.winnerUserId();
            } catch (Exception e) {
                log.warn("피지컬 틱 처리 오류 room={}: {}", s.game.roomCode(), e.getMessage());
            } finally {
                s.lock.unlock();
            }
            if (winnerUserId != null) concludeWin(s.game.roomCode(), winnerUserId);
        }
    }

    // ===================== 브로드캐스트 =====================

    private void broadcast(PhysicalGame game) {
        messagingTemplate.convertAndSend("/topic/room/" + game.roomCode() + "/physical", toSnapshot(game));
    }

    private PhysicalSnapshot toSnapshot(PhysicalGame game) {
        long now = System.currentTimeMillis();

        List<PhysicalPlayerView> playerViews = new ArrayList<>();
        for (PhysicalPlayer p : game.players()) {
            playerViews.add(new PhysicalPlayerView(
                    p.getColor().name(),
                    p.getNickname(),
                    p.getX(),
                    p.getY(),
                    p.getHeldItem() != null ? p.getHeldItem().name() : null,
                    p.getLastDestroyAt() + props.destroyCooldownMs(),
                    p.isSpeedBoosted(now),
                    p.getSkin(),
                    p.getCharacter(),
                    p.getSoundAssetKey()
            ));
        }

        List<ItemDropView> itemViews = new ArrayList<>();
        for (ItemDrop d : game.drops()) {
            itemViews.add(new ItemDropView(d.x(), d.y(), d.type().name()));
        }

        StoneColor winner = game.winnerColor();
        int[][] pendingWinLine = null;
        if (game.hasPendingWin()) {
            List<int[]> line = game.board().findWinningLine(game.pendingWinColor(), game.winCount());
            if (!line.isEmpty()) pendingWinLine = line.toArray(new int[0][]);
        }
        return new PhysicalSnapshot(
                game.roomCode(),
                game.status().name(),
                game.board().size(),
                game.winCount(),
                game.board().encode(),
                playerViews,
                itemViews,
                winner != null ? winner.name() : null,
                game.hasPendingWin() ? game.pendingWinColor().name() : null,
                pendingWinLine,
                now
        );
    }
}
