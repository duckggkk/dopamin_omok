package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.GameResponse;
import com.dopamin.omok.game.application.dto.GameSummaryResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface GetMyGamesUseCase {
    Page<GameResponse> getMyGames(Long userId, Pageable pageable);
    Page<GameSummaryResponse> getPublicGames(UUID publicId, Long viewerUserId, Pageable pageable);
}
