package com.dopamin.omok.shop.application.port.out;

import com.dopamin.omok.shop.domain.UserItem;

import java.util.List;
import java.util.Optional;

public interface LoadUserItemPort {
    List<UserItem> findUserItemsByUserId(Long userId);
    Optional<UserItem> findByUserIdAndItemId(Long userId, Long itemId);
    boolean existsByUserIdAndItemId(Long userId, Long itemId);
}
