package com.dopamin.omok.shop.domain.validation;

import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;
import org.springframework.stereotype.Component;

/**
 * 패배 이펙트(DEFEAT_EFFECT) config 검증.
 * 승자가 장착하면 패자 화면에 뜨는 연출 — effect 키(화이트리스트) 필수.
 * 적용 효과는 서버가 user_active_items 에서 읽어 게임 상태로 내려주므로 미보유 우회 불가.
 */
@Component
public class DefeatEffectConfigValidator implements ItemConfigValidator {

    @Override
    public ItemType supportedType() {
        return ItemType.DEFEAT_EFFECT;
    }

    @Override
    public void validate(ItemConfig config) {
        ConfigChecks.requireConfig(config);
        ConfigChecks.requireDisplayName(config);
        ConfigChecks.requireDefeatEffect(config);
    }
}
