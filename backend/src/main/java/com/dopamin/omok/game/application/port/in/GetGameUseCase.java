package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.GameResponse;

public interface GetGameUseCase {
    GameResponse getGame(String roomCode);
}
