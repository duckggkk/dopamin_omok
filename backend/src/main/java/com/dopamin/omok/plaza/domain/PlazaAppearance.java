package com.dopamin.omok.plaza.domain;

/**
 * 아바타 외형(레이어 키 묶음). 클라가 레이어별 스프라이트시트를 골라 겹쳐 그린다.
 * Phase 1 은 플레이스홀더 키만 — 소유권 검증 없이 서버가 그대로 중계한다.
 * Phase 2 에서 user_active_items(서버 권위 장착)로 교체 예정.
 */
public record PlazaAppearance(
        String body,
        String hair,
        String outfit,
        String accessory
) {
    public static final PlazaAppearance DEFAULT = new PlazaAppearance("base", "hair_01", "outfit_01", null);

    public PlazaAppearance {
        if (body == null || body.isBlank()) body = "base";
    }
}
