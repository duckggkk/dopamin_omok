package com.dopamin.omok.shop.application.service;

import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.shop.application.port.out.AssetPort;
import com.dopamin.omok.shop.application.port.out.LoadItemPort;
import com.dopamin.omok.shop.application.port.out.LoadUserItemPort;
import com.dopamin.omok.shop.domain.AssetResult;
import com.dopamin.omok.shop.domain.Item;
import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock private AssetPort assetPort;
    @Mock private LoadItemPort loadItemPort;
    @Mock private LoadUserItemPort loadUserItemPort;
    @InjectMocks private AssetService assetService;

    @Test
    @DisplayName("착수음은 미보유 사용자도 받을 수 있다(상대/관전자 재생용) — 소유권 검사 안 함")
    void stoneSoundSkipsOwnership() {
        Item item = mock(Item.class);
        when(item.getItemConfig()).thenReturn(new ItemConfig("나무", "wood", null, null, null, null, null));
        when(loadItemPort.findByType(ItemType.STONE_SOUND)).thenReturn(List.of(item));
        AssetResult.Data data = new AssetResult.Data(new byte[]{1, 2, 3}, "audio/mp4");
        when(assetPort.load(ItemType.STONE_SOUND, "wood")).thenReturn(data);

        AssetResult result = assetService.getAsset(999L, ItemType.STONE_SOUND, "wood");

        assertThat(result).isSameAs(data);
        verify(loadUserItemPort, never()).existsByUserIdAndItemId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("바둑판 스킨은 미보유면 거부된다(소유권 필요)")
    void boardSkinRequiresOwnership() {
        Item item = mock(Item.class);
        when(item.getItemConfig()).thenReturn(new ItemConfig("대리석", "marble", null, null, null, null, null));
        when(item.getId()).thenReturn(2L);
        when(loadItemPort.findByType(ItemType.BOARD_SKIN)).thenReturn(List.of(item));
        when(loadUserItemPort.existsByUserIdAndItemId(999L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> assetService.getAsset(999L, ItemType.BOARD_SKIN, "marble"))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ITEM_NOT_OWNED));

        verify(assetPort, never()).load(any(), any());
    }
}
