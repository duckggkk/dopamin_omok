package com.dopamin.omok.game.application.dto;

/**
 * 참가자 한 명에 대해 서버가 해석한 코스메틱 묶음(바둑알 스킨 색 + 착수 효과 키).
 * RoomService 가 user_active_items 에서 권위 있게 채워 RoomResponse 로 내려보낸다.
 * 둘 다 없으면 EMPTY(스킨/효과 미적용).
 */
public record PlayerCosmetics(StoneSkinResponse stoneSkin, String stoneEffect) {

    public static final PlayerCosmetics EMPTY = new PlayerCosmetics(null, null);
}
