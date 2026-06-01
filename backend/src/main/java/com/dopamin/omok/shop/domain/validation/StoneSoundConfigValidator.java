package com.dopamin.omok.shop.domain.validation;

import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;
import org.springframework.stereotype.Component;

/**
 * 착수음 config 검증.
 * 오디오 파일(assetKey) 필수, 색상/필터는 사용하지 않음.
 */
@Component
public class StoneSoundConfigValidator implements ItemConfigValidator {

    @Override
    public ItemType supportedType() {
        return ItemType.STONE_SOUND;
    }

    @Override
    public void validate(ItemConfig config) {
        ConfigChecks.requireConfig(config);
        ConfigChecks.requireDisplayName(config);
        ConfigChecks.requireAssetKey(config);
    }
}
