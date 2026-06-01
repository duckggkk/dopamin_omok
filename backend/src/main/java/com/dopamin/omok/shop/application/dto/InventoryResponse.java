package com.dopamin.omok.shop.application.dto;

import java.util.List;
import java.util.Map;

public record InventoryResponse(int currency, List<ItemResponse> items, Map<String, ItemResponse> activeItems) {}
