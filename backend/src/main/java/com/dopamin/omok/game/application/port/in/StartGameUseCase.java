package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.RoomResponse;

public interface StartGameUseCase {
    RoomResponse startGame(String roomCode, Long userId);
}
