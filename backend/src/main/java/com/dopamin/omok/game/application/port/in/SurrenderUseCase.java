package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.GameResponse;

public interface SurrenderUseCase {
    GameResponse surrender(String roomCode, Long userId);
}
