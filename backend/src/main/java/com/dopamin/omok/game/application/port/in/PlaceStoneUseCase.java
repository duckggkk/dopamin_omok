package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.GameMoveResponse;

public interface PlaceStoneUseCase {
    GameMoveResponse placeStone(String roomCode, Long userId, int row, int col);
}
