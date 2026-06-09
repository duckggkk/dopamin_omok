package com.dopamin.omok.shop.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

public record EquipItemRequest(
        @NotNull(message = "아이템 ID는 필수입니다.")
        Long itemId
) {}
