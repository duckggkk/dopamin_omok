package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.GameMove;

import java.util.List;

public interface LoadGameMovesPort {
    List<GameMove> findByGameIdOrderByMoveNumberAsc(Long gameId);
}
