package com.dopamin.omok.shop.application.port.in;

import com.dopamin.omok.shop.application.dto.InventoryResponse;

public interface GetInventoryUseCase {
    InventoryResponse getInventory(Long userId);
}
