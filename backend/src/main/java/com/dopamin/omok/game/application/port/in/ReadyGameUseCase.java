package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.RoomResponse;

public interface ReadyGameUseCase {
    RoomResponse readyGame(String roomCode, Long userId);
}
