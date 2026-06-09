package com.dopamin.omok.game.application.dto;

import com.dopamin.omok.game.domain.*;
import com.dopamin.omok.user.application.dto.UserResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
        return of(room, players, currentGame, null, Map.of());
    }

    public static RoomResponse of(Room room, List<GamePlayer> players, Game currentGame, String winnerDefeatMessage) {
        return of(room, players, currentGame, winnerDefeatMessage, Map.of());
    }

    /**
     * cosmetics: 참가자 내부 userId(Long) → 서버가 해석한 코스메틱 묶음(바둑알 스킨 색 + 착수 효과).
     * 전적으로 user_active_items 에서 읽은 값이라 클라이언트 입력이 아니다.
     * 미보유/미장착 플레이어는 키가 없어 EMPTY(null 스킨/효과)로 내려간다.
     */
    public static RoomResponse of(Room room, List<GamePlayer> players, Game currentGame,
                                  String winnerDefeatMessage, Map<Long, PlayerCosmetics> cosmetics) {
        Map<Long, PlayerCosmetics> cos = cosmetics != null ? cosmetics : Map.of();
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
                players.stream()
                        .map(gp -> GamePlayerResponse.from(gp,
                                cos.getOrDefault(gp.getUser().getId(), PlayerCosmetics.EMPTY)))
                        .toList(),
                currentGame != null ? GameResponse.from(currentGame, winnerDefeatMessage) : null,
                room.getCreatedAt()
        );
    }

    public static RoomResponse of(Room room, List<GamePlayer> players) {
        return of(room, players, null, null, Map.of());
    }
}
