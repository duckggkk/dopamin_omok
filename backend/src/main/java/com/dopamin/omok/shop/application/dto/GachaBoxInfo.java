package com.dopamin.omok.shop.application.dto;

import com.dopamin.omok.shop.domain.ItemType;

import java.util.List;

/** possibleItems: 상자에서 나올 수 있는 아이템 목록(설정 포함) — 프론트가 스킨/효과/착수음 미리보기를 렌더한다. */
public record GachaBoxInfo(String type, String name, int price, ItemType itemType, List<ItemResponse> possibleItems) {}
