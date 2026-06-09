package com.dopamin.omok.shop.domain.validation;

import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;
import org.springframework.stereotype.Component;

/**
 * 착수 효과(STONE_EFFECT) config 검증.
 * 색과 무관한 절차적 애니메이션 — effect 키(화이트리스트) 필수.
 * 적용 효과는 서버가 user_active_items 에서 읽어 방 상태로 내려주므로 미보유 우회 불가.
 */
@Component
public class StoneEffectConfigValidator implements ItemConfigValidator {

    @Override
    public ItemType supportedType() {
        return ItemType.STONE_EFFECT;
    }

    @Override
    public void validate(ItemConfig config) {
        ConfigChecks.requireConfig(config);
        ConfigChecks.requireDisplayName(config);
        ConfigChecks.requireEffect(config);
    }
}
