package com.dopamin.omok.shop.application.service;

import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.shop.application.port.in.GrantDefaultItemsUseCase;
import com.dopamin.omok.shop.application.port.out.*;
import com.dopamin.omok.shop.domain.Item;
import com.dopamin.omok.shop.domain.UserActiveItem;
import com.dopamin.omok.shop.domain.UserItem;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 기본 지급 아이템(default_grant=true)을 유저에게 지급하고 미장착 슬롯에 자동 장착한다.
 * 가입 시 호출되며, 이미 보유/장착된 경우는 건너뛰어 멱등하게 동작한다.
 */
@Service
@RequiredArgsConstructor
public class GrantDefaultItemsService implements GrantDefaultItemsUseCase {

    private final LoadItemPort loadItemPort;
    private final LoadUserPort loadUserPort;
    private final LoadUserItemPort loadUserItemPort;
    private final SaveUserItemPort saveUserItemPort;
    private final LoadUserActiveItemPort loadUserActiveItemPort;
    private final SaveUserActiveItemPort saveUserActiveItemPort;

    @Override
    @Transactional
    public void grantDefaults(Long userId) {
        List<Item> defaults = loadItemPort.findDefaultGrantItems();
        if (defaults.isEmpty()) return;

        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));

        for (Item item : defaults) {
            if (!loadUserItemPort.existsByUserIdAndItemId(userId, item.getId())) {
                saveUserItemPort.save(UserItem.of(user, item));
            }
            // 해당 타입 활성 슬롯이 비어 있으면 기본 아이템을 장착
            if (loadUserActiveItemPort.findByUserIdAndItemType(userId, item.getItemType()).isEmpty()) {
                saveUserActiveItemPort.save(UserActiveItem.of(user, item.getItemType(), item));
            }
        }
    }
}
