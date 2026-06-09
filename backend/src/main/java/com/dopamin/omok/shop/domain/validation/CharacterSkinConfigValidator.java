package com.dopamin.omok.shop.domain.validation;

import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;
import org.springframework.stereotype.Component;

/**
 * 피지컬 오목 캐릭터 스킨(CHARACTER_SKIN) config 검증.
 * 절차적(색상 + face 키워드)이라 에셋이 없고, 적용 캐릭터는 서버가 user_active_items 에서 읽어
 * 스냅샷으로 내려주므로 미보유 우회 불가(유료재화 보호).
 */
@Component
public class CharacterSkinConfigValidator implements ItemConfigValidator {

    @Override
    public ItemType supportedType() {
        return ItemType.CHARACTER_SKIN;
    }

    @Override
    public void validate(ItemConfig config) {
        ConfigChecks.requireConfig(config);
        ConfigChecks.requireDisplayName(config);
        ConfigChecks.requireCharacter(config);
    }
}
