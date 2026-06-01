package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.RoomResponse;

public interface SpectateRoomUseCase {
    RoomResponse spectateRoom(String roomCode, Long userId);
}
