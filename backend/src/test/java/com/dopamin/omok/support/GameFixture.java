package com.dopamin.omok.support;

import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameMove;
import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.user.domain.User;

import java.lang.reflect.Field;

public class GameFixture {

    public static Game createWaiting(Long gameId, User blackPlayer) {
        Game game = Game.createRoom(blackPlayer, "ROOM" + gameId);
        setField(game, "id", gameId);
        return game;
    }

    public static Game createWaiting(User blackPlayer) {
        return createWaiting(1L, blackPlayer);
    }

    public static Game createInProgress(Long gameId, User blackPlayer, User whitePlayer) {
        Game game = createWaiting(gameId, blackPlayer);
        game.joinAsWhitePlayer(whitePlayer);
        return game;
    }

    public static Game createInProgress(User blackPlayer, User whitePlayer) {
        return createInProgress(1L, blackPlayer, whitePlayer);
    }

    public static GameMove createMove(Game game, User player, StoneColor color, int row, int col, int moveNumber) {
        GameMove move = GameMove.of(game, player, color, row, col, moveNumber);
        setField(move, "id", (long) moveNumber);
        return move;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
