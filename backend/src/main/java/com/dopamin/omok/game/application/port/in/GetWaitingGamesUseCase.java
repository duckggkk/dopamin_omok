package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.GameRoomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetWaitingGamesUseCase {
    Page<GameRoomResponse> getWaitingGames(Pageable pageable);
}
