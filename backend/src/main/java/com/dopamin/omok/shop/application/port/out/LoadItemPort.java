package com.dopamin.omok.shop.application.port.out;

import com.dopamin.omok.shop.domain.Item;
import com.dopamin.omok.shop.domain.ItemType;

import java.util.List;
import java.util.Optional;

public interface LoadItemPort {
    Optional<Item> findById(Long id);

    /** 해당 타입 전체 (에셋 서빙·검증 등에 사용) */
    List<Item> findByType(ItemType itemType);

    /** 뽑기 풀 — 기본 지급 아이템 제외 */
    List<Item> findGachaPoolByType(ItemType itemType);

    /** 가입 시 전원 자동 지급 대상 아이템 */
    List<Item> findDefaultGrantItems();
}
