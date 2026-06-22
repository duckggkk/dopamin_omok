package com.dopamin.omok.game.physical.application;

import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;
import com.dopamin.omok.game.physical.application.dto.PhysicalSnapshot;
import com.dopamin.omok.game.physical.application.dto.PhysicalSnapshot.ItemDropView;
import com.dopamin.omok.game.physical.application.dto.PhysicalSnapshot.PhysicalPlayerView;
import com.dopamin.omok.game.physical.application.dto.PhysicalTrainingLog;
import com.dopamin.omok.game.physical.application.port.out.PhysicalEventPublisherPort;
import com.dopamin.omok.game.physical.bot.BotDecision;
import com.dopamin.omok.game.physical.bot.PhysicalBotDriver;
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
import java.util.Collection;
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
    private final PhysicalBotDriver botDriver;

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    private static final class Session {
        final PhysicalGame game;
        final ReentrantLock lock = new ReentrantLock();
        final Recorder recorder;
        final TrainingRecorder training;
        Session(PhysicalGame game, boolean trainingEnabled) {
            this.game = game;
            long start = System.currentTimeMillis();
            this.recorder = new Recorder(game.board().size(), start);
            this.training = new TrainingRecorder(start, trainingEnabled);
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

    /**
     * ML 학습용 행동 로그 기록기 — 양 플레이어의 입력/결정 + 아이템 스폰을 시간순으로 모은다.
     * 활성(trainingLogEnabled) 시에만 동작하고, 어뷰징/메모리 대비 상한을 둔다. 세션 락 하에서만 호출되어 스레드 안전.
     */
    private static final class TrainingRecorder {
        private static final int MAX_ACTIONS = 20000;
        final long startMs;
        final boolean enabled;
        final List<PhysicalTrainingLog.StartState> starts = new ArrayList<>();
        final List<PhysicalTrainingLog.Action> actions = new ArrayList<>();
        final List<PhysicalTrainingLog.ItemSpawn> spawns = new ArrayList<>();

        TrainingRecorder(long startMs, boolean enabled) {
            this.startMs = startMs;
            this.enabled = enabled;
        }

        void recordStarts(Collection<PhysicalPlayer> players) {
            if (!enabled) return;
            for (PhysicalPlayer p : players) {
                starts.add(new PhysicalTrainingLog.StartState(
                        p.getColor().name(), p.getNickname(), p.getX(), p.getY(), p.isBot()));
            }
        }

        void recordAction(long nowMs, StoneColor color, String type, Direction dir, int x, int y) {
            if (!enabled || actions.size() >= MAX_ACTIONS) return;
            actions.add(new PhysicalTrainingLog.Action(
                    nowMs - startMs, color.name(), type, dir != null ? dir.name() : null, x, y));
        }

        void recordSpawn(long nowMs, ItemDrop drop) {
            if (!enabled || spawns.size() >= MAX_ACTIONS) return;
            spawns.add(new PhysicalTrainingLog.ItemSpawn(nowMs - startMs, drop.x(), drop.y(), drop.type().name()));
        }

        PhysicalTrainingLog build() {
            if (!enabled) return null;
            return new PhysicalTrainingLog(List.copyOf(starts), List.copyOf(actions), List.copyOf(spawns));
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
        Session session = new Session(game, props.trainingLogEnabled());
        session.training.recordStarts(game.players());
        sessions.put(game.roomCode(), session);
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
            if (game.isCountingDown(now)) return; // 시작 카운트다운 동안엔 입력 무시(서버 권위)
            int preX = player.getX(), preY = player.getY(); // 행동 직전 위치(학습 로그 상태 앵커)
            switch (type) {
                case MOVE_START -> engine.startMove(game, player, direction, now);
                case MOVE_STOP -> engine.stopMove(player);
                case PLACE -> engine.place(game, player, now); // 승리는 settle 경과 후 tick에서 확정
                case DESTROY -> engine.destroy(game, player, now);
                case USE_ITEM -> engine.useItem(game, player, now);
            }
            // 보드 칸은 사람 입력 또는 (봇이 있으면) 틱에서 바뀐다 → 양쪽 모두에서 diff 기록한다.
            s.recorder.capture(game.board().encode(), now);
            s.training.recordAction(now, player.getColor(), type.name(), direction, preX, preY);
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
                players, List.copyOf(s.recorder.events), s.training.build());
    }

    /** 봇 결정 종류를 학습 로그용 입력 타입 문자열로 매핑(사람 입력과 어휘 통일). IDLE 은 기록 안 함. */
    private String botActionType(BotDecision d) {
        return switch (d.action().kind()) {
            case MOVE -> "MOVE_START";
            case PLACE -> "PLACE";
            case DESTROY -> "DESTROY";
            case USE_ITEM -> "USE_ITEM";
            case IDLE -> null;
        };
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
                // 시작 카운트다운 중엔 보드/캐릭터만 세팅된 채 동결 — 스냅샷만 갱신(클라가 카운트다운 표시)
                if (game.isCountingDown(now)) { broadcast(game); continue; }

                // 봇이 있으면 매 틱 결정 → 사람과 같은 엔진 호출로 적용. 결정은 학습 로그에 남긴다.
                List<BotDecision> decisions = botDriver.drive(game, now);
                for (BotDecision d : decisions) {
                    String type = botActionType(d);
                    if (type != null) {
                        s.training.recordAction(now, d.color(), type, d.action().direction(), d.x(), d.y());
                    }
                }

                int dropsBefore = game.drops().size();
                engine.tickMovement(game, now);
                engine.maybeSpawnItem(game, now);
                if (game.drops().size() > dropsBefore) { // 이번 틱에 스폰된 아이템(랜덤) 기록
                    s.training.recordSpawn(now, game.drops().get(game.drops().size() - 1));
                }
                engine.settlePendingWin(game, now); // 5목이 settle 동안 유지됐으면 여기서 승리 확정

                // 봇 행동으로 보드가 바뀌었을 수 있으니 틱에서도 칸 변화를 기록한다(사람 게임은 변화 없어 비용 0).
                if (!decisions.isEmpty()) s.recorder.capture(game.board().encode(), now);

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
                now,
                game.playStartAt()
        );
    }
}
