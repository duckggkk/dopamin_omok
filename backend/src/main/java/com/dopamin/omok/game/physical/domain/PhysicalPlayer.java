package com.dopamin.omok.game.physical.domain;

import com.dopamin.omok.game.application.dto.CharacterSkinResponse;
import com.dopamin.omok.game.application.dto.StoneSkinResponse;
import com.dopamin.omok.game.domain.StoneColor;
import lombok.Getter;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 피지컬 오목 한 플레이어의 실시간 상태(메모리). 모든 변형은 세션 락 하에서만 호출된다.
 * skin 은 서버가 user_active_items 에서 권위 있게 해석한 바둑알 스킨 색(미장착 시 null) — 클라 입력을 신뢰하지 않는다.
 */
@Getter
public class PhysicalPlayer {

    private final Long userId;
    private final String nickname;
    private final StoneColor color;
    private final StoneSkinResponse skin;          // nullable — 장착 바둑알 스킨 색
    private final CharacterSkinResponse character;  // nullable — 장착 캐릭터 스킨
    private final String soundAssetKey;             // nullable — 장착 착수음 assetKey

    private int x;
    private int y;
    private Direction intent;            // 현재 이동 의도(없으면 null)
    private long lastMoveAt;
    private long lastPlaceAt;
    private long lastDestroyAt;
    // 입력 버퍼 — 쿨다운에 막혀 즉시 실행 못 한 입력을 잠깐 기억했다가 쿨다운 풀리면 실행(입력 씹힘 방지).
    // 이동은 순서 보존 큐(FIFO): 방향을 빠르게 번갈아 눌러도 누른 순서대로 한 칸씩 소화한다(덮어쓰기 X).
    private final Deque<Direction> moveQueue = new ArrayDeque<>();
    private long placeQueuedAt;          // 버퍼된 착수 시각(0=없음)
    private PhysicalItemType heldItem;   // 보유 아이템(1슬롯, 없으면 null)
    private long speedBoostUntil;        // 이동 부스트 만료 시각(epoch ms)
    private boolean bot;                 // true = AI 봇이 조종(드라이버가 입력 생성). 기본 false(사람)

    public PhysicalPlayer(Long userId, String nickname, StoneColor color, StoneSkinResponse skin,
                          CharacterSkinResponse character, String soundAssetKey, int x, int y) {
        this.userId = userId;
        this.nickname = nickname;
        this.color = color;
        this.skin = skin;
        this.character = character;
        this.soundAssetKey = soundAssetKey;
        this.x = x;
        this.y = y;
    }

    public void setIntent(Direction intent) {
        this.intent = intent;
    }

    public void clearIntent() {
        this.intent = null;
    }

    public void moveTo(int x, int y, long now) {
        this.x = x;
        this.y = y;
        this.lastMoveAt = now;
    }

    public void markPlaced(long now) {
        this.lastPlaceAt = now;
    }

    public void markDestroyed(long now) {
        this.lastDestroyAt = now;
    }

    /** 이동 입력을 큐 끝에 추가(상한 초과 시 무시 — 무한 누적/폭주 방지). */
    public void enqueueMove(Direction dir, int max) {
        if (dir != null && moveQueue.size() < max) moveQueue.addLast(dir);
    }

    public Direction peekMove() {
        return moveQueue.peekFirst();
    }

    public Direction pollMove() {
        return moveQueue.pollFirst();
    }

    public boolean hasQueuedMove() {
        return !moveQueue.isEmpty();
    }

    public void clearMoveQueue() {
        moveQueue.clear();
    }

    public void queuePlace(long now) {
        this.placeQueuedAt = now;
    }

    public void clearPlaceQueue() {
        this.placeQueuedAt = 0;
    }

    public void hold(PhysicalItemType item) {
        this.heldItem = item;
    }

    public void clearItem() {
        this.heldItem = null;
    }

    public void startSpeedBoost(long until) {
        this.speedBoostUntil = until;
    }

    public boolean isSpeedBoosted(long now) {
        return now < speedBoostUntil;
    }

    /** 이 플레이어를 AI 봇으로 표시한다(드라이버가 매 틱 입력을 생성). */
    public void markAsBot() {
        this.bot = true;
    }
}
