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
    private final PhysicalBoard board;
    private final Map<Long, PhysicalPlayer> playersByUserId = new HashMap<>();
    private final Map<StoneColor, PhysicalPlayer> playersByColor = new EnumMap<>(StoneColor.class);
    private final List<ItemDrop> drops = new ArrayList<>();

    private GameStatus status = GameStatus.IN_PROGRESS;
    private StoneColor winnerColor;
    private Long winnerUserId;
    private long lastItemSpawnAt;

    // 승리 확정 지연(settle): 5목 완성 시 즉시 끝내지 않고, lockAt 까지 유지되면 확정한다.
    private StoneColor pendingWinColor;
    private long pendingWinLockAt;

    public PhysicalGame(String roomCode, Long gameId, int boardSize, int winCount, long startedAt) {
        this.roomCode = roomCode;
        this.gameId = gameId;
        this.winCount = winCount;
        this.board = new PhysicalBoard(boardSize);
        this.lastItemSpawnAt = startedAt;
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

    public long lastItemSpawnAt() {
        return lastItemSpawnAt;
    }

    public void setLastItemSpawnAt(long now) {
        this.lastItemSpawnAt = now;
    }

    public boolean hasPendingWin() {
        return pendingWinColor != null;
    }

    public StoneColor pendingWinColor() {
        return pendingWinColor;
    }

    public long pendingWinLockAt() {
        return pendingWinLockAt;
    }

    /** 5목 완성 → 확정 대기 시작(먼저 완성한 쪽이 점유; 이미 대기 중이면 유지). */
    public void startPendingWin(StoneColor color, long lockAt) {
        if (pendingWinColor == null) {
            this.pendingWinColor = color;
            this.pendingWinLockAt = lockAt;
        }
    }

    public void clearPendingWin() {
        this.pendingWinColor = null;
    }

    /** 승자(색) 확정 후 종료. winnerColor=null 이면 무효/포기 종료(승자 없음). */
    public void finish(StoneColor winnerColor) {
        this.status = GameStatus.FINISHED;
        this.winnerColor = winnerColor;
        this.winnerUserId = winnerColor != null && playersByColor.get(winnerColor) != null
                ? playersByColor.get(winnerColor).getUserId()
                : null;
        this.pendingWinColor = null;
    }
}
