package com.dopamin.omok.shop.application.dto;

import com.dopamin.omok.shop.domain.Item;
import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;

public record ItemResponse(
        Long id,
        String name,
        String displayName,
        ItemType itemType,
        String description,
        ItemConfig itemConfig
) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getName(),
                item.getDisplayName(),
                item.getItemType(),
                item.getDescription(),
                item.getItemConfig()
        );
    }
}
