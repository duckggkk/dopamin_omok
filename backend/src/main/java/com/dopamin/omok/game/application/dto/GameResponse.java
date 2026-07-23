package com.dopamin.omok.game.application.dto;

import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.user.application.dto.PublicUserResponse;

import java.time.LocalDateTime;

public record GameResponse(
        Long id,
        Integer gameNumber,
        GameStatus status,
        PublicUserResponse blackPlayer,
        PublicUserResponse whitePlayer,
        PublicUserResponse winner,
        StoneColor currentTurn,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime lastMoveAt,
        String winnerDefeatMessage,
        // 승자가 장착한 패배 이펙트 키(패자 화면 연출용, 미장착 null) — DEFEAT_EFFECT
        String winnerDefeatEffect
) {
    public static GameResponse from(Game game) {
        return from(game, null, null);
    }

    public static GameResponse from(Game game, String winnerDefeatMessage, String winnerDefeatEffect) {
        return new GameResponse(
                game.getId(),
                game.getGameNumber(),
                game.getStatus(),
                game.getBlackPlayer() != null ? PublicUserResponse.from(game.getBlackPlayer()) : null,
                game.getWhitePlayer() != null ? PublicUserResponse.from(game.getWhitePlayer()) : null,
                game.getWinner() != null ? PublicUserResponse.from(game.getWinner()) : null,
                game.getCurrentTurn(),
                game.getStartedAt(),
                game.getFinishedAt(),
                game.getLastMoveAt(),
                winnerDefeatMessage,
                winnerDefeatEffect
        );
    }
}
