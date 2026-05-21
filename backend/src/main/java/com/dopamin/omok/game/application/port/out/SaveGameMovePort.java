package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.GameMove;

public interface SaveGameMovePort {
    GameMove save(GameMove move);
}
