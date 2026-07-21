package com.dopamin.omok.game.application.dto;

import com.dopamin.omok.shop.domain.ItemConfig;

/**
 * 바둑알 스킨(절차적) — 색상만. 방 상태에 플레이어별로 실려 전원(상대/관전자)에게 브로드캐스트된다.
 * 서버가 user_active_items 의 장착 STONE_SKIN 에서 읽어 내려주므로 클라이언트 입력을 신뢰하지 않는다.
 *
 * @param fill 바둑알 내부를 채우는 hex 색상
 * @param stroke 바둑알 테두리의 hex 색상
 * @param shine 바둑알 광택 하이라이트의 hex 색상
 */
public record StoneSkinResponse(String fill, String stroke, String shine) {

    /**
     * 아이템의 바둑알 스타일 설정을 응답 객체로 변환한다.
     *
     * @param stone 변환할 바둑알 스타일 설정
     * @return 바둑알 스킨 응답
     */
    public static StoneSkinResponse from(ItemConfig.StoneStyle stone) {
        return new StoneSkinResponse(stone.fill(), stone.stroke(), stone.shine());
    }
}
