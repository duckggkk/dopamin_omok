package com.dopamin.omok.shop.adapter.out.persistence;

import com.dopamin.omok.shop.domain.ItemType;
import com.dopamin.omok.shop.domain.UserActiveItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserActiveItemJpaRepository extends JpaRepository<UserActiveItem, Long> {
    List<UserActiveItem> findByUserId(Long userId);
    Optional<UserActiveItem> findByUserIdAndItemType(Long userId, ItemType itemType);

    @Query("SELECT ua.item.name FROM UserActiveItem ua WHERE ua.user.id = :userId AND ua.itemType = :itemType")
    Optional<String> findItemNameByUserIdAndItemType(@Param("userId") Long userId,
                                                     @Param("itemType") ItemType itemType);
}
