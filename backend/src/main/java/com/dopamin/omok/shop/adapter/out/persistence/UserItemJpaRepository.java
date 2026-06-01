package com.dopamin.omok.shop.adapter.out.persistence;

import com.dopamin.omok.shop.domain.UserItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserItemJpaRepository extends JpaRepository<UserItem, Long> {
    List<UserItem> findByUserId(Long userId);
    Optional<UserItem> findByUserIdAndItemId(Long userId, Long itemId);
    boolean existsByUserIdAndItemId(Long userId, Long itemId);
}
