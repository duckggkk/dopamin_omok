package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.Game;

public interface SaveGamePort {
    Game save(Game game);
}
