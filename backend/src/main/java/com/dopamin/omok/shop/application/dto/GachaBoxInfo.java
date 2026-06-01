package com.dopamin.omok.shop.application.dto;

import com.dopamin.omok.shop.domain.ItemType;

import java.util.List;

public record GachaBoxInfo(String type, String name, int price, ItemType itemType, List<String> possibleItems) {}
