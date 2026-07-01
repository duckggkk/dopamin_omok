package com.dopamin.omok.game.physical.domain;

import com.dopamin.omok.game.application.dto.CharacterSkinResponse;
import com.dopamin.omok.game.application.dto.StoneSkinResponse;
import com.dopamin.omok.game.domain.StoneColor;
import lombok.Getter;

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
    // 연속(부드러운) 이동용 실수 위치(칸 단위). 격자 모드에선 항상 x,y 와 같고,
    // 연속 모드에선 이쪽이 권위이고 x,y 는 가장 가까운 교차점(반올림)으로 파생된다 — 착수/파괴/획득이 x,y 를 쓴다.
    private double fx;
    private double fy;
    private Direction intent;            // 현재 이동 의도(없으면 null)
    private long lastMoveAt;
    private long lastPlaceAt;
    private long lastDestroyAt;
    // 이동 입력 버퍼 — 쿨다운에 막혀 즉시 실행 못 한 방향을 잠깐 기억했다가 쿨다운 풀리면 1회 실행(입력 씹힘 방지).
    // 단일 슬롯이라 새 입력이 이전 버퍼를 덮어써 '최신 입력이 우선'된다(미리 여러 번 눌러도 옛 방향이 잔여 이동으로 튀지 않음).
    private Direction bufferedMove;
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
        this.fx = x;
        this.fy = y;
    }

    /** 렌더/스냅샷용 위치(칸 단위 실수). 격자 모드에선 x,y 와 동일, 연속 모드에선 소수 좌표. */
    public double getRenderX() {
        return fx;
    }

    public double getRenderY() {
        return fy;
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
        this.fx = x;
        this.fy = y;
        this.lastMoveAt = now;
    }

    /**
     * 연속(부드러운) 이동 — 실수 좌표를 갱신하고, 착수/파괴/획득이 쓰는 정수 칸(x,y)을
     * 가장 가까운 교차점으로 반올림해 함께 맞춘다.
     */
    public void moveContinuous(double fx, double fy, long now) {
        this.fx = fx;
        this.fy = fy;
        this.x = (int) Math.round(fx);
        this.y = (int) Math.round(fy);
        this.lastMoveAt = now;
    }

    public void markPlaced(long now) {
        this.lastPlaceAt = now;
    }

    public void markDestroyed(long now) {
        this.lastDestroyAt = now;
    }

    /** 이동 입력을 버퍼에 넣는다 — 단일 슬롯이라 이전 값을 덮어쓴다(최신 입력 우선). */
    public void bufferMove(Direction dir) {
        if (dir != null) this.bufferedMove = dir;
    }

    public Direction peekBufferedMove() {
        return bufferedMove;
    }

    /** 버퍼된 이동을 1회 소비한다(꺼내고 비움). */
    public Direction consumeBufferedMove() {
        Direction dir = bufferedMove;
        bufferedMove = null;
        return dir;
    }

    public boolean hasBufferedMove() {
        return bufferedMove != null;
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
