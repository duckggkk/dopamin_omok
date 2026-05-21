package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.GameRoomResponse;

public interface GetGameUseCase {
    GameRoomResponse getGame(String roomCode);
}
