package com.dopamin.omok.game.physical.domain;

import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.StoneColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 진행 중인 피지컬 오목 한 판의 메모리 상태(보드 + 두 플레이어 + 필드 아이템 + 상태).
 * DB에 저장되지 않으며, 결과(승패)만 종료 시 기존 games 테이블에 기록된다.
 * 모든 접근/변형은 세션 락 하에서만 이뤄진다(스레드 안전성은 호출 측 보장).
 */
public class PhysicalGame {

    private final String roomCode;
    private final Long gameId;
    private final int winCount;
    private final int targetScore;
    private final PhysicalBoard board;
    private final Map<Long, PhysicalPlayer> playersByUserId = new HashMap<>();
    private final Map<StoneColor, PhysicalPlayer> playersByColor = new EnumMap<>(StoneColor.class);
    private final List<ItemDrop> drops = new ArrayList<>();

    // 색별 누적 점수(오목 1개 완성 = 1점). targetScore 에 먼저 도달하면 승리.
    private final Map<StoneColor, Integer> scores = new EnumMap<>(StoneColor.class);

    // 충전 중(확정 대기)인 완성 라인들 — 여러 줄이 동시에 차오를 수 있다(각자 독립 게이지/lockAt).
    private final List<PendingLine> pendingLines = new ArrayList<>();

    // 라인 파괴(득점) 1회성 신호 — 클라가 seq 증가를 감지해 직전 틱에 사라진 라인들의 특수효과를 재생한다.
    private int scoreEventSeq;
    private List<PendingLine> lastClearedLines = List.of(); // 직전 틱에 파괴+득점된 라인들(없으면 빈 리스트)

    private GameStatus status = GameStatus.IN_PROGRESS;
    private StoneColor winnerColor;
    private Long winnerUserId;
    private long lastItemSpawnAt;

    // 이 시각(epoch ms)부터 실제 플레이가 시작된다. 그 전까지는 카운트다운 구간(보드/캐릭터만 세팅, 입력·이동·스폰 정지).
    private final long playStartAt;

    // true 면 캐릭터가 교차점에 고정되지 않고 연속(소수) 좌표로 부드럽게 움직인다(크아식). 착수는 가장 가까운 교차점에.
    // 현재는 로컬 개발용 '혼자 두기' 샌드박스(솔로)만 켜며, 실대전/봇 대국은 기존 격자 이동(false)을 유지한다.
    private boolean continuousMovement;

    public PhysicalGame(String roomCode, Long gameId, int boardSize, int winCount, int targetScore,
                        long startedAt, long countdownMs) {
        this.roomCode = roomCode;
        this.gameId = gameId;
        this.winCount = winCount;
        this.targetScore = Math.max(1, targetScore);
        this.board = new PhysicalBoard(boardSize);
        this.playStartAt = startedAt + Math.max(0, countdownMs);
        this.lastItemSpawnAt = this.playStartAt;   // 아이템 스폰 시계는 카운트다운이 끝난 뒤부터 흐른다
        this.scores.put(StoneColor.BLACK, 0);
        this.scores.put(StoneColor.WHITE, 0);
    }

    public void addPlayer(PhysicalPlayer player) {
        playersByUserId.put(player.getUserId(), player);
        playersByColor.put(player.getColor(), player);
    }

    public PhysicalPlayer playerOf(Long userId) {
        return playersByUserId.get(userId);
    }

    public PhysicalPlayer playerOfColor(StoneColor color) {
        return playersByColor.get(color);
    }

    public Collection<PhysicalPlayer> players() {
        return playersByUserId.values();
    }

    public PhysicalPlayer opponentOf(PhysicalPlayer player) {
        StoneColor other = player.getColor() == StoneColor.BLACK ? StoneColor.WHITE : StoneColor.BLACK;
        return playersByColor.get(other);
    }

    public PhysicalBoard board() {
        return board;
    }

    public List<ItemDrop> drops() {
        return drops;
    }

    public String roomCode() {
        return roomCode;
    }

    public Long gameId() {
        return gameId;
    }

    public int winCount() {
        return winCount;
    }

    public int targetScore() {
        return targetScore;
    }

    public int scoreOf(StoneColor color) {
        return scores.getOrDefault(color, 0);
    }

    /** 해당 색 점수 +1 후 새 점수를 반환한다(오목 1개 완성 = 1점). */
    public int addScore(StoneColor color) {
        int next = scoreOf(color) + 1;
        scores.put(color, next);
        return next;
    }

    public int scoreEventSeq() {
        return scoreEventSeq;
    }

    public List<PendingLine> lastClearedLines() {
        return lastClearedLines;
    }

    /** 이번 틱에 파괴+득점된 라인들을 기록 — 1회성 신호(seq 증가)로 클라가 줄별 특수효과를 재생한다. */
    public void recordClearedLines(List<PendingLine> cleared) {
        if (cleared.isEmpty()) return;
        this.lastClearedLines = List.copyOf(cleared);
        this.scoreEventSeq += cleared.size();
    }

    public List<PendingLine> pendingLines() {
        return pendingLines;
    }

    /**
     * 현재 보드의 완성 라인 전체({@code current})와 충전 상태를 대조해 갱신한다(매 틱 호출).
     * - 기존 충전과 '같은 줄'(같은 색·공유 칸 2개 이상 = 동일선/연장/일부 끊겨 줄어든 경우)이면 lockAt 을 이어받아
     *   게이지를 리셋하지 않는다 → 5목을 6·7목으로 늘리거나, 6목의 끝이 끊겨 5목만 남아도 충전이 계속된다.
     * - 매칭되는 기존 충전이 없는 새 줄은 {@code newLockAt} 으로 충전을 시작한다.
     * - 더 이상 완성 라인이 아닌(끊겨 사라진) 기존 충전은 자동으로 빠진다.
     * 방향이 다른 포크는 피벗 1칸만 공유(<2)하므로 별개 줄로 각각 충전된다.
     */
    public void reconcilePendingLines(List<PendingLine> current, long newLockAt) {
        List<PendingLine> old = new ArrayList<>(pendingLines);
        boolean[] used = new boolean[old.size()];
        List<PendingLine> next = new ArrayList<>(current.size());
        for (PendingLine cur : current) {
            long lockAt = newLockAt;
            boolean matched = false;
            for (int i = 0; i < old.size(); i++) {
                PendingLine o = old.get(i);
                if (used[i] || o.color() != cur.color() || sharedCellCount(o.cells(), cur.cells()) < 2) continue;
                lockAt = matched ? Math.min(lockAt, o.lockAt()) : o.lockAt(); // 가장 오래 충전된 타이머 유지
                matched = true;
                used[i] = true;
            }
            next.add(new PendingLine(cur.color(), cur.cells(), lockAt));
        }
        pendingLines.clear();
        pendingLines.addAll(next);
    }

    private static int sharedCellCount(List<int[]> a, List<int[]> b) {
        int n = 0;
        for (int[] p : a) {
            for (int[] q : b) {
                if (p[0] == q[0] && p[1] == q[1]) { n++; break; }
            }
        }
        return n;
    }

    public GameStatus status() {
        return status;
    }

    public StoneColor winnerColor() {
        return winnerColor;
    }

    public Long winnerUserId() {
        return winnerUserId;
    }

    public boolean isInProgress() {
        return status == GameStatus.IN_PROGRESS;
    }

    public long playStartAt() {
        return playStartAt;
    }

    /** 이 판을 연속(부드러운) 이동으로 전환한다 — 시작 구성 시 1회 호출(솔로 샌드박스). */
    public void enableContinuousMovement() {
        this.continuousMovement = true;
    }

    public boolean isContinuousMovement() {
        return continuousMovement;
    }

    /** 시작 카운트다운(보드/캐릭터만 세팅되고 입력·이동이 멈춘) 구간인지. */
    public boolean isCountingDown(long now) {
        return isInProgress() && now < playStartAt;
    }

    public long lastItemSpawnAt() {
        return lastItemSpawnAt;
    }

    public void setLastItemSpawnAt(long now) {
        this.lastItemSpawnAt = now;
    }

    /** 승자(색) 확정 후 종료. winnerColor=null 이면 무효/포기 종료(승자 없음). */
    public void finish(StoneColor winnerColor) {
        this.status = GameStatus.FINISHED;
        this.winnerColor = winnerColor;
        this.winnerUserId = winnerColor != null && playersByColor.get(winnerColor) != null
                ? playersByColor.get(winnerColor).getUserId()
                : null;
        this.pendingLines.clear();
    }
}
