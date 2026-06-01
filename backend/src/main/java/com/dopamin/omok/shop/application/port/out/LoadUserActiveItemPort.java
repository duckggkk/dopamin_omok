package com.dopamin.omok.shop.application.port.out;

import com.dopamin.omok.shop.domain.ItemType;
import com.dopamin.omok.shop.domain.UserActiveItem;

import java.util.List;
import java.util.Optional;

public interface LoadUserActiveItemPort {
    List<UserActiveItem> findUserActiveItemsByUserId(Long userId);
    Optional<UserActiveItem> findByUserIdAndItemType(Long userId, ItemType itemType);
    Optional<String> findActiveItemNameByUserIdAndType(Long userId, ItemType itemType);
}
