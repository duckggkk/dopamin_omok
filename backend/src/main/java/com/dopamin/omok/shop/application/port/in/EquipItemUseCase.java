package com.dopamin.omok.shop.application.port.in;

import com.dopamin.omok.shop.domain.ItemType;

public interface EquipItemUseCase {
    void equipItem(Long userId, Long itemId);

    /** 바둑알 스킨(STONE_SKIN)만 장착한다. 다른 타입이면 거부 — 방 내 스킨 변경 등 용도 제한 경로용. */
    void equipStoneSkin(Long userId, Long itemId);

    /** 해당 타입의 장착을 해제한다(기본값으로 되돌림). 장착된 게 없으면 무동작. */
    void unequip(Long userId, ItemType itemType);
}
