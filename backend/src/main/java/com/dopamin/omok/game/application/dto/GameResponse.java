package com.dopamin.omok.game.application.dto;

import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.user.application.dto.UserResponse;

import java.time.LocalDateTime;

public record GameResponse(
        Long id,
        Integer gameNumber,
        GameStatus status,
        UserResponse blackPlayer,
        UserResponse whitePlayer,
        UserResponse winner,
        StoneColor currentTurn,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime lastMoveAt,
        String winnerDefeatMessage
) {
    public static GameResponse from(Game game) {
        return from(game, null);
    }

    public static GameResponse from(Game game, String winnerDefeatMessage) {
        return new GameResponse(
                game.getId(),
                game.getGameNumber(),
                game.getStatus(),
                game.getBlackPlayer() != null ? UserResponse.from(game.getBlackPlayer()) : null,
                game.getWhitePlayer() != null ? UserResponse.from(game.getWhitePlayer()) : null,
                game.getWinner() != null ? UserResponse.from(game.getWinner()) : null,
                game.getCurrentTurn(),
                game.getStartedAt(),
                game.getFinishedAt(),
                game.getLastMoveAt(),
                winnerDefeatMessage
        );
    }
}
