package com.dopamin.omok.game.application.dto;

import com.dopamin.omok.game.domain.*;
import com.dopamin.omok.user.application.dto.UserResponse;

import java.time.LocalDateTime;
import java.util.List;

public record RoomResponse(
        Long id,
        String roomCode,
        UserResponse host,
        RoomStatus status,
        GameType gameType,
        TimeLimit timeLimit,
        ByoyomiOption byoyomiOption,
        Integer maxSpectators,
        Integer currentGameNumber,
        List<GamePlayerResponse> players,
        GameResponse currentGame,
        LocalDateTime createdAt
) {
    public static RoomResponse of(Room room, List<GamePlayer> players, Game currentGame) {
        return of(room, players, currentGame, null);
    }

    public static RoomResponse of(Room room, List<GamePlayer> players, Game currentGame, String winnerDefeatMessage) {
        return new RoomResponse(
                room.getId(),
                room.getRoomCode(),
                UserResponse.from(room.getHost()),
                room.getStatus(),
                room.getGameType(),
                room.getTimeLimit(),
                room.getByoyomiOption(),
                room.getMaxSpectators(),
                room.getCurrentGameNumber(),
                players.stream().map(GamePlayerResponse::from).toList(),
                currentGame != null ? GameResponse.from(currentGame, winnerDefeatMessage) : null,
                room.getCreatedAt()
        );
    }

    public static RoomResponse of(Room room, List<GamePlayer> players) {
        return of(room, players, null, null);
    }
}
