package com.dopamin.omok.game.application.dto;

import com.dopamin.omok.game.domain.GameMove;
import com.dopamin.omok.game.domain.StoneColor;

import java.time.LocalDateTime;

public record GameMoveResponse(
        Long id,
        String roomCode,
        Long playerId,
        String playerNickname,
        StoneColor color,
        Integer row,
        Integer col,
        Integer moveNumber,
        LocalDateTime createdAt
) {
    public static GameMoveResponse from(GameMove move) {
        return new GameMoveResponse(
                move.getId(),
                move.getGame().getRoomCode(),
                move.getPlayer().getId(),
                move.getPlayer().getNickname(),
                move.getColor(),
                move.getRow(),
                move.getCol(),
                move.getMoveNumber(),
                move.getCreatedAt()
        );
    }
}
