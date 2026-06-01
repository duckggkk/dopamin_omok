package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.GamePlayer;

public interface SaveGamePlayerPort {
    GamePlayer save(GamePlayer gamePlayer);
}
