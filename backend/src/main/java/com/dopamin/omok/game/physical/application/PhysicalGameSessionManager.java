package com.dopamin.omok.game.physical.application;

import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;
import com.dopamin.omok.game.physical.application.dto.PhysicalSnapshot;
import com.dopamin.omok.game.physical.application.dto.PhysicalSnapshot.ItemDropView;
import com.dopamin.omok.game.physical.application.dto.PhysicalSnapshot.PhysicalPlayerView;
import com.dopamin.omok.game.physical.application.port.out.PhysicalEventPublisherPort;
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
    private final PhysicalEventPublisherPort snapshotPublisher;
    private final ApplicationEventPublisher eventPublisher;

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    private static final class Session {
        final PhysicalGame game;
        final ReentrantLock lock = new ReentrantLock();
        final Recorder recorder;
        Session(PhysicalGame game) {
            this.game = game;
            this.recorder = new Recorder(game.board().size(), System.currentTimeMillis());
        }
    }

    /**
     * 리플레이 기록기 — 보드 칸 변화만 시간순으로 모은다(스냅샷 아님, 이벤트만).
     * 입력 직후 직전 보드와 diff 해 바뀐 칸만 append → 양이 행동/쿨다운에 묶여 작다.
     * 어뷰징 대비 상한(MAX_EVENTS)을 둔다. 세션 락 하에서만 호출되어 스레드 안전.
     */
    private static final class Recorder {
        private static final int MAX_EVENTS = 4000;
        final long startMs;
        final int size;
        final int[][] prev;
        final List<PhysicalReplayData.CellChange> events = new ArrayList<>();

        Recorder(int size, long startMs) {
            this.size = size;
            this.startMs = startMs;
            this.prev = new int[size][size];
        }

        void capture(int[][] cells, long nowMs) {
            if (events.size() >= MAX_EVENTS) return;
            long t = nowMs - startMs;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (cells[y][x] != prev[y][x]) {
                        events.add(new PhysicalReplayData.CellChange(t, x, y, cells[y][x]));
                        prev[y][x] = cells[y][x];
                        if (events.size() >= MAX_EVENTS) return;
                    }
                }
            }
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

    /**
     * 강제 정리(연결 끊김/방장 퇴장). 승자 없이 종료 표시 후 최종 스냅샷만 보낸다(이벤트 미발행).
     * @return 그때까지 기록된 리플레이(피지컬 세션이 있었을 때만, 없으면 null). 저장은 호출 측(RoomService)이 한다.
     */
    public PhysicalReplayData stopSession(String roomCode) {
        Session s = sessions.remove(roomCode);
        if (s == null) return null;
        s.lock.lock();
        try {
            if (s.game.isInProgress()) s.game.finish(null);
            broadcast(s.game);
            return buildReplay(s);
        } finally {
            s.lock.unlock();
        }
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
            // 보드 칸은 입력에서만 바뀐다(틱은 이동/스폰만) → 입력 직후에만 diff 기록하면 충분.
            s.recorder.capture(game.board().encode(), now);
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

    /** 세션 제거 + 승자 확정 이벤트 발행(락 밖에서 — DB I/O가 세션 락을 잡지 않게 함). 리플레이를 함께 실어 보낸다. */
    private void concludeWin(String roomCode, Long winnerUserId) {
        Session s = sessions.remove(roomCode);
        PhysicalReplayData replay = (s != null) ? buildReplay(s) : null;
        eventPublisher.publishEvent(new PhysicalGameEndedEvent(roomCode, winnerUserId, replay));
        log.debug("피지컬 세션 종료(승자 확정): room={} winnerUserId={}", roomCode, winnerUserId);
    }

    /** 종료 시점의 세션 상태 + 기록된 칸 변화로 리플레이 데이터를 만든다. */
    private PhysicalReplayData buildReplay(Session s) {
        PhysicalGame g = s.game;
        List<PhysicalReplayData.PlayerInfo> players = new ArrayList<>();
        for (PhysicalPlayer p : g.players()) {
            players.add(new PhysicalReplayData.PlayerInfo(p.getColor().name(), p.getNickname()));
        }
        String winner = g.winnerColor() != null ? g.winnerColor().name() : null;
        long durationMs = System.currentTimeMillis() - s.recorder.startMs;
        return new PhysicalReplayData(
                g.gameId(), g.board().size(), g.winCount(), durationMs, winner,
                players, List.copyOf(s.recorder.events));
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
        snapshotPublisher.publishSnapshot(game.roomCode(), toSnapshot(game));
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
