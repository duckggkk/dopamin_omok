package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.RoomResponse;

public interface JoinRoomUseCase {
    RoomResponse joinRoom(String roomCode, Long userId);
}
