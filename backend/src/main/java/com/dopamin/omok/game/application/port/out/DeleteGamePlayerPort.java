package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.GamePlayer;

public interface DeleteGamePlayerPort {
    void delete(GamePlayer gamePlayer);
    void deleteByRoomId(Long roomId);
}
