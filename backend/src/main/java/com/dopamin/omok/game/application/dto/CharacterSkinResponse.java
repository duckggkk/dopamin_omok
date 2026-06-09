package com.dopamin.omok.game.application.dto;

import com.dopamin.omok.shop.domain.ItemConfig;

/**
 * 피지컬 오목 캐릭터 스킨(절차적) — body(몸 색)·accent(테두리 색)은 hex, face는 프론트 이모지 매핑 키워드.
 * 서버가 user_active_items 의 장착 CHARACTER_SKIN 에서 읽어 스냅샷으로 내려주므로 클라 입력을 신뢰하지 않는다.
 */
public record CharacterSkinResponse(String body, String accent, String face) {

    public static CharacterSkinResponse from(ItemConfig.CharacterStyle character) {
        return new CharacterSkinResponse(character.body(), character.accent(), character.face());
    }
}
