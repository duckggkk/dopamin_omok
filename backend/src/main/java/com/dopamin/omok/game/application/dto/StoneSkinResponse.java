package com.dopamin.omok.game.application.dto;

import com.dopamin.omok.shop.domain.ItemConfig;

/**
 * 바둑알 스킨(절차적) — 색상만. 방 상태에 플레이어별로 실려 전원(상대/관전자)에게 브로드캐스트된다.
 * 서버가 user_active_items 의 장착 STONE_SKIN 에서 읽어 내려주므로 클라이언트 입력을 신뢰하지 않는다.
 */
public record StoneSkinResponse(String fill, String stroke, String shine) {

    public static StoneSkinResponse from(ItemConfig.StoneStyle stone) {
        return new StoneSkinResponse(stone.fill(), stone.stroke(), stone.shine());
    }
}
