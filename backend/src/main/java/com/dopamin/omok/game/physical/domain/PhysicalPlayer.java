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
    private Direction intent;            // 현재 이동 의도(없으면 null)
    private long lastMoveAt;
    private long lastPlaceAt;
    private long lastDestroyAt;
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
