package com.dopamin.omok.shop.adapter.in.web.dto;

import com.dopamin.omok.shop.domain.ItemType;
import jakarta.validation.constraints.NotNull;

public record UnequipItemRequest(
        @NotNull(message = "아이템 타입은 필수입니다.")
        ItemType itemType
) {}
