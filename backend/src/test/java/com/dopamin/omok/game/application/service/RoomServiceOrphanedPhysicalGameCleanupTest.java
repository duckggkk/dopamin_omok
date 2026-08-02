package com.dopamin.omok.game.application.service;

import com.dopamin.omok.game.application.port.out.DeleteGamePlayerPort;
import com.dopamin.omok.game.application.port.out.LoadGamePlayerPort;
import com.dopamin.omok.game.application.port.out.LoadGamePort;
import com.dopamin.omok.game.application.port.out.LoadRoomPort;
import com.dopamin.omok.game.application.port.out.RoomEventPublisherPort;
import com.dopamin.omok.game.application.port.out.SaveGamePlayerPort;
import com.dopamin.omok.game.application.port.out.SaveGamePort;
import com.dopamin.omok.game.application.port.out.SaveRoomPort;
import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.physical.application.PhysicalGameLifecycle;
import com.dopamin.omok.game.physical.application.port.out.SavePhysicalReplayPort;
import com.dopamin.omok.global.websocket.DisconnectGraceManager;
import com.dopamin.omok.global.websocket.WebSocketSessionRegistry;
import com.dopamin.omok.shop.application.port.out.LoadUserActiveItemPort;
import com.dopamin.omok.shop.application.port.in.EquipItemUseCase;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceOrphanedPhysicalGameCleanupTest {

    @Mock DisconnectGraceManager disconnectGraceManager;
    @Mock WebSocketSessionRegistry sessionRegistry;
    @Mock LoadRoomPort loadRoomPort;
    @Mock SaveRoomPort saveRoomPort;
    @Mock LoadGamePlayerPort loadGamePlayerPort;
    @Mock SaveGamePlayerPort saveGamePlayerPort;
    @Mock DeleteGamePlayerPort deleteGamePlayerPort;
    @Mock LoadGamePort loadGamePort;
    @Mock SaveGamePort saveGamePort;
    @Mock LoadUserPort loadUserPort;
    @Mock LoadUserActiveItemPort loadUserActiveItemPort;
    @Mock EquipItemUseCase equipItemUseCase;
    @Mock RoomEventPublisherPort roomEventPublisherPort;
    @Mock PhysicalGameLifecycle physicalGameLifecycle;
    @Mock SavePhysicalReplayPort savePhysicalReplayPort;

    @InjectMocks
    RoomService roomService;

    @Test
    void abandonsActivePhysicalGameAndClosesItsRoom() {
        Game game = org.mockito.Mockito.mock(Game.class);
        Room room = org.mockito.Mockito.mock(Room.class);
        when(loadGamePort.findActivePhysicalGamesForUpdate()).thenReturn(List.of(game));
        when(game.getRoom()).thenReturn(room);
        when(game.getId()).thenReturn(99L);
        when(room.getId()).thenReturn(10L);
        when(room.getRoomCode()).thenReturn("PHYSICAL01");

        int cleaned = roomService.cleanupOrphanedPhysicalGames();

        assertThat(cleaned).isEqualTo(1);
        var stateChanges = inOrder(game, room, saveGamePort, saveRoomPort);
        stateChanges.verify(game).abandon();
        stateChanges.verify(room).close();
        stateChanges.verify(saveGamePort).save(game);
        stateChanges.verify(saveRoomPort).save(room);
        verify(deleteGamePlayerPort).deleteByRoomId(10L);
        verify(disconnectGraceManager).clearRoom("PHYSICAL01");
        verify(roomEventPublisherPort).publishClosed(
                "PHYSICAL01", "서버 재시작으로 진행 중이던 피지컬 대국이 종료되었습니다.");
    }

    @Test
    void doesNothingWhenNoOrphanedPhysicalGameExists() {
        when(loadGamePort.findActivePhysicalGamesForUpdate()).thenReturn(List.of());

        int cleaned = roomService.cleanupOrphanedPhysicalGames();

        assertThat(cleaned).isZero();
        verifyNoInteractions(saveGamePort, saveRoomPort, deleteGamePlayerPort, roomEventPublisherPort);
    }
}
