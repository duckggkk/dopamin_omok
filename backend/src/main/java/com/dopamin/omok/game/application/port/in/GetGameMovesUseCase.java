package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.GameMoveResponse;

import java.util.List;

public interface GetGameMovesUseCase {
    List<GameMoveResponse> getGameMoves(String roomCode);
}
