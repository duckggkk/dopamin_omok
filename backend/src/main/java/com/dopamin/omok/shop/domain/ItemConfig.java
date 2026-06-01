package com.dopamin.omok.shop.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 코스메틱 아이템 메타데이터 값 객체 (스킨, 착수음 등 공통).
 * items.item_config (JSON) 컬럼에 직렬화되어 저장된다.
 *
 * 공통 필드: displayName, assetKey
 * 타입별 선택 필드: colors/filter (BOARD_SKIN 전용)
 *
 * 보호 에셋(이미지/오디오)을 가진 아이템은 assetKey로 백엔드 보호 엔드포인트에서 받아온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemConfig(
        String displayName,
        String assetKey,
        BoardColors colors,
        SvgFilter filter
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BoardColors(String bg, String lines, String dots) {}

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
}
