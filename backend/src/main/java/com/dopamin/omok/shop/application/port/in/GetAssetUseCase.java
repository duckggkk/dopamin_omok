package com.dopamin.omok.shop.application.port.in;

import com.dopamin.omok.shop.domain.AssetResult;
import com.dopamin.omok.shop.domain.ItemType;

public interface GetAssetUseCase {
    AssetResult getAsset(Long userId, ItemType itemType, String assetKey);
}
