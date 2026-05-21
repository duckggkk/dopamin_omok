package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.GameRoomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetMyGamesUseCase {
    Page<GameRoomResponse> getMyGames(Long userId, Pageable pageable);
}
