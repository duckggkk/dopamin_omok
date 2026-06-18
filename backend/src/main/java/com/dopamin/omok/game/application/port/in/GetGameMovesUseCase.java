package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.GameMoveResponse;

import java.util.List;
import java.util.UUID;

public interface GetGameMovesUseCase {
    List<GameMoveResponse> getGameMoves(String roomCode);

    /** 기보 보기: 종료된 특정 게임(gameId)의 착수 목록. 해당 게임 참가자만 조회 가능. */
    List<GameMoveResponse> getGameMovesByGameId(Long gameId, Long userId);

    List<GameMoveResponse> getPublicGameMovesByGameId(UUID publicId, Long gameId, Long viewerUserId);
}
