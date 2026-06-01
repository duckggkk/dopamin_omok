package com.dopamin.omok.shop.domain.validation;

import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;
import org.springframework.stereotype.Component;

/**
 * 바둑판 스킨 config 검증.
 * 색상 필수 + (filter 또는 assetKey 중 하나 이상) 필요.
 */
@Component
public class BoardSkinConfigValidator implements ItemConfigValidator {

    @Override
    public ItemType supportedType() {
        return ItemType.BOARD_SKIN;
    }

    @Override
    public void validate(ItemConfig config) {
        ConfigChecks.requireConfig(config);
        ConfigChecks.requireDisplayName(config);
        ConfigChecks.requireColors(config);

        if (!config.hasFilter() && !config.hasAsset()) {
            throw new InvalidItemConfigException("스킨은 filter 또는 assetKey 중 하나 이상이 필요합니다.");
        }
        if (config.hasFilter()) ConfigChecks.validateFilter(config.filter());
        ConfigChecks.validateAssetKeyIfPresent(config);
    }
}
