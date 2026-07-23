package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.GameSummaryResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface GetMyGamesUseCase {
    /** 내 전적 목록. 남의 전적({@link #getPublicGames})과 같은 요약 형태로 내려준다. */
    Page<GameSummaryResponse> getMyGames(Long userId, Pageable pageable);
    Page<GameSummaryResponse> getPublicGames(UUID publicId, Long viewerUserId, Pageable pageable);
}
