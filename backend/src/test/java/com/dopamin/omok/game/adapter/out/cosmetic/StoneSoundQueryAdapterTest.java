package com.dopamin.omok.game.adapter.out.cosmetic;

import com.dopamin.omok.shop.application.port.out.LoadUserActiveItemPort;
import com.dopamin.omok.shop.domain.Item;
import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;
import com.dopamin.omok.shop.domain.UserActiveItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoneSoundQueryAdapterTest {

    @Mock
    private LoadUserActiveItemPort loadUserActiveItemPort;
    @InjectMocks
    private StoneSoundQueryAdapter adapter;

    @Test
    @DisplayName("장착 착수음이 있으면 assetKey 를 반환")
    void returnsAssetKeyWhenEquipped() {
        UserActiveItem active = mock(UserActiveItem.class);
        Item item = mock(Item.class);
        when(loadUserActiveItemPort.findByUserIdAndItemType(1L, ItemType.STONE_SOUND))
                .thenReturn(Optional.of(active));
        when(active.getItem()).thenReturn(item);
        when(item.getItemConfig()).thenReturn(new ItemConfig("백돌소리", "stone/click.m4a", null, null, null, null, null));

        assertThat(adapter.findEquippedStoneSoundKey(1L)).contains("stone/click.m4a");
    }

    @Test
    @DisplayName("착수음 미장착이면 빈 결과")
    void emptyWhenNotEquipped() {
        when(loadUserActiveItemPort.findByUserIdAndItemType(1L, ItemType.STONE_SOUND))
                .thenReturn(Optional.empty());

        assertThat(adapter.findEquippedStoneSoundKey(1L)).isEmpty();
    }
}
