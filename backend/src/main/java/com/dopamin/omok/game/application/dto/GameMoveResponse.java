package com.dopamin.omok.game.application.dto;

import com.dopamin.omok.game.domain.GameMove;
import com.dopamin.omok.game.domain.StoneColor;

import java.time.LocalDateTime;
import java.util.UUID;

public record GameMoveResponse(
        Long id,
        String roomCode,
        UUID playerId,
        String playerNickname,
        StoneColor color,
        Integer row,
        Integer col,
        Integer moveNumber,
        // 이 수를 둔 사람이 장착한 착수음 assetKey. 양쪽 클라이언트가 동일하게 재생(미장착 시 null).
        String soundAssetKey,
        LocalDateTime createdAt
) {
    public static GameMoveResponse from(GameMove move) {
        return from(move, null);
    }

    public static GameMoveResponse from(GameMove move, String soundAssetKey) {
        return new GameMoveResponse(
                move.getId(),
                move.getGame().getRoomCode(),
                move.getPlayer().getPublicId(),
                move.getPlayer().getNickname(),
                move.getColor(),
                move.getRow(),
                move.getCol(),
                move.getMoveNumber(),
                soundAssetKey,
                move.getCreatedAt()
        );
    }
}
