package com.dopamin.omok.game.application.dto;

import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.user.application.dto.UserResponse;

import java.time.LocalDateTime;

public record GameRoomResponse(
        Long id,
        String roomCode,
        GameStatus status,
        UserResponse blackPlayer,
        UserResponse whitePlayer,
        UserResponse winner,
        StoneColor currentTurn,
        Integer boardSize,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
    public static GameRoomResponse from(Game game) {
        return new GameRoomResponse(
                game.getId(),
                game.getRoomCode(),
                game.getStatus(),
                game.getBlackPlayer() != null ? UserResponse.from(game.getBlackPlayer()) : null,
                game.getWhitePlayer() != null ? UserResponse.from(game.getWhitePlayer()) : null,
                game.getWinner() != null ? UserResponse.from(game.getWinner()) : null,
                game.getCurrentTurn(),
                game.getBoardSize(),
                game.getCreatedAt(),
                game.getStartedAt(),
                game.getFinishedAt()
        );
    }
}
