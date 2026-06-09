package com.dopamin.omok.shop.domain.validation;

import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;
import org.springframework.stereotype.Component;

/**
 * 바둑알 스킨 config 검증.
 * 절차적(색상만) 스킨이라 에셋이 없고 stone(fill/stroke/shine) 색상이 필수.
 * 적용 스킨은 서버가 user_active_items 에서 읽어 방 상태로 내려주므로 미보유 우회 불가.
 */
@Component
public class StoneSkinConfigValidator implements ItemConfigValidator {

    @Override
    public ItemType supportedType() {
        return ItemType.STONE_SKIN;
    }

    @Override
    public void validate(ItemConfig config) {
        ConfigChecks.requireConfig(config);
        ConfigChecks.requireDisplayName(config);
        ConfigChecks.requireStone(config);
        ConfigChecks.validateEffectIfPresent(config); // 고급 스킨이 애니메이션을 번들할 수 있음
    }
}
