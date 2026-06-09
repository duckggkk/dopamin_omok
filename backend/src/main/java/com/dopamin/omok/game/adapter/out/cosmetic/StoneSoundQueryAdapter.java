package com.dopamin.omok.game.adapter.out.cosmetic;

import com.dopamin.omok.game.application.port.out.LoadStoneSoundPort;
import com.dopamin.omok.shop.application.port.out.LoadUserActiveItemPort;
import com.dopamin.omok.shop.domain.Item;
import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;
import com.dopamin.omok.shop.domain.UserActiveItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * game 모듈이 shop 의 장착 착수음을 조회하기 위한 어댑터.
 * (game 애플리케이션은 LoadStoneSoundPort 추상화에만 의존하고, 실제 shop 연동은 이 어댑터가 담당)
 */
@Component
@RequiredArgsConstructor
public class StoneSoundQueryAdapter implements LoadStoneSoundPort {

    private final LoadUserActiveItemPort loadUserActiveItemPort;

    @Override
    public Optional<String> findEquippedStoneSoundKey(Long userId) {
        return loadUserActiveItemPort.findByUserIdAndItemType(userId, ItemType.STONE_SOUND)
                .map(UserActiveItem::getItem)
                .map(Item::getItemConfig)
                .map(ItemConfig::assetKey);
    }
}
