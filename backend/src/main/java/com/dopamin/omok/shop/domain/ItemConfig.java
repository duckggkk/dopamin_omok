package com.dopamin.omok.shop.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 코스메틱 아이템 메타데이터 값 객체 (스킨, 착수음 등 공통).
 * items.item_config (JSON) 컬럼에 직렬화되어 저장된다.
 *
 * 공통 필드: displayName, assetKey
 * 타입별 선택 필드: colors/filter (BOARD_SKIN 전용), stone (STONE_SKIN 전용),
 *                  effect (STONE_EFFECT 전용 — 단, 고급 STONE_SKIN이 애니메이션을 번들할 때도 사용)
 *
 * 보호 에셋(이미지/오디오)을 가진 아이템은 assetKey로 백엔드 보호 엔드포인트에서 받아온다.
 * STONE_SKIN(바둑알 스킨)·STONE_EFFECT(착수 효과)는 절차적이라 에셋이 없고, 색상/효과키가 방 상태로 내려간다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemConfig(
        String displayName,
        String assetKey,
        BoardColors colors,
        SvgFilter filter,
        StoneStyle stone,
        String effect,
        CharacterStyle character
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BoardColors(String bg, String lines, String dots) {}

    /** 바둑알 스킨 스타일(절차적) — fill(채움), stroke(테두리), shine(광택 하이라이트). 모두 hex. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StoneStyle(String fill, String stroke, String shine) {}

    /**
     * 피지컬 오목 캐릭터 스킨(절차적). body(몸 색)·accent(테두리 색)은 hex,
     * face 는 프론트가 이모지로 매핑하는 안전 키워드(예: robot, rabbit). 4바이트 이모지를 DB에 넣지 않기 위함.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CharacterStyle(String body, String accent, String face) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SvgFilter(
            String type,
            double freqX,
            double freqY,
            int octaves,
            int seed,
            String blend
    ) {}

    public boolean hasFilter() {
        return filter != null;
    }

    public boolean hasAsset() {
        return assetKey != null && !assetKey.isBlank();
    }

    public boolean hasColors() {
        return colors != null;
    }

    public boolean hasStone() {
        return stone != null;
    }

    public boolean hasEffect() {
        return effect != null && !effect.isBlank();
    }

    public boolean hasCharacter() {
        return character != null;
    }
}
