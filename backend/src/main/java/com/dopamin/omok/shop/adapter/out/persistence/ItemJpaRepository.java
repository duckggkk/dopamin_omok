package com.dopamin.omok.shop.adapter.out.persistence;

import com.dopamin.omok.shop.domain.Item;
import com.dopamin.omok.shop.domain.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemJpaRepository extends JpaRepository<Item, Long> {
    List<Item> findByItemType(ItemType itemType);

    // 뽑기 풀 — 기본 지급 아이템은 제외
    List<Item> findByItemTypeAndDefaultGrantFalse(ItemType itemType);

    // 가입 시 전원 지급 대상
    List<Item> findByDefaultGrantTrue();
}
