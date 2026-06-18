package com.dopamin.omok.shop.adapter.out.persistence;

import com.dopamin.omok.shop.application.port.out.*;
import com.dopamin.omok.shop.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ShopPersistenceAdapter implements
        LoadItemPort, LoadUserItemPort, SaveUserItemPort,
        LoadUserActiveItemPort, SaveUserActiveItemPort {

    private final ItemJpaRepository itemRepository;
    private final UserItemJpaRepository userItemRepository;
    private final UserActiveItemJpaRepository userActiveItemRepository;

    @Override
    public Optional<Item> findById(Long id) {
        return itemRepository.findById(id);
    }

    @Override
    public List<Item> findByType(ItemType itemType) {
        return itemRepository.findByItemType(itemType);
    }

    @Override
    public List<Item> findGachaPoolByType(ItemType itemType) {
        return itemRepository.findByItemTypeAndDefaultGrantFalse(itemType);
    }

    @Override
    public List<Item> findDefaultGrantItems() {
        return itemRepository.findByDefaultGrantTrue();
    }

    @Override
    public List<UserItem> findUserItemsByUserId(Long userId) {
        return userItemRepository.findByUserId(userId);
    }

    @Override
    public Optional<UserItem> findByUserIdAndItemId(Long userId, Long itemId) {
        return userItemRepository.findByUserIdAndItemId(userId, itemId);
    }

    @Override
    public boolean existsByUserIdAndItemId(Long userId, Long itemId) {
        return userItemRepository.existsByUserIdAndItemId(userId, itemId);
    }

    @Override
    public void save(UserItem userItem) {
        userItemRepository.save(userItem);
    }

    @Override
    public List<UserActiveItem> findUserActiveItemsByUserId(Long userId) {
        return userActiveItemRepository.findByUserId(userId);
    }

    @Override
    public Optional<UserActiveItem> findByUserIdAndItemType(Long userId, ItemType itemType) {
        return userActiveItemRepository.findByUserIdAndItemType(userId, itemType);
    }

    @Override
    public Optional<String> findActiveItemNameByUserIdAndType(Long userId, ItemType itemType) {
        return userActiveItemRepository.findItemNameByUserIdAndItemType(userId, itemType);
    }

    @Override
    public void save(UserActiveItem userActiveItem) {
        userActiveItemRepository.save(userActiveItem);
    }

    @Override
    public void delete(UserActiveItem userActiveItem) {
        userActiveItemRepository.delete(userActiveItem);
    }
}
