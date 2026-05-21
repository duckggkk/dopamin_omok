package com.dopamin.omok.game.application.service;

import com.dopamin.omok.game.application.dto.GameMoveResponse;
import com.dopamin.omok.game.application.dto.GameRoomResponse;
import com.dopamin.omok.game.application.port.in.*;
import com.dopamin.omok.game.application.port.out.LoadGameMovesPort;
import com.dopamin.omok.game.application.port.out.LoadGamePort;
import com.dopamin.omok.game.application.port.out.SaveGameMovePort;
import com.dopamin.omok.game.application.port.out.SaveGamePort;
import com.dopamin.omok.game.domain.*;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.domain.User;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService implements CreateGameRoomUseCase, JoinGameRoomUseCase, PlaceStoneUseCase,
        SurrenderUseCase, GetGameUseCase, GetGameMovesUseCase, GetWaitingGamesUseCase, GetMyGamesUseCase {

    private final LoadGamePort loadGamePort;
    private final SaveGamePort saveGamePort;
    private final LoadGameMovesPort loadGameMovesPort;
    private final SaveGameMovePort saveGameMovePort;
    private final LoadUserPort loadUserPort;
    private final OmokGameEngine gameEngine;

    @Override
    @Transactional
    public GameRoomResponse createRoom(Long userId) {
        User user = findUserById(userId);
        String roomCode = generateUniqueRoomCode();
        Game game = Game.createRoom(user, roomCode);
        saveGamePort.save(game);
        return GameRoomResponse.from(game);
    }

    @Override
    @Transactional
    public GameRoomResponse joinRoom(String roomCode, Long userId) {
        Game game = loadGamePort.findByRoomCode(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.GAME_NOT_FOUND));

        if (!game.isWaiting()) {
            throw new OmokException(ErrorCode.GAME_ALREADY_FULL);
        }

        if (game.isParticipant(userId)) {
            return GameRoomResponse.from(game);
        }

        User user = findUserById(userId);
        game.joinAsWhitePlayer(user);
        return GameRoomResponse.from(game);
    }

    @Override
    @Transactional
    public GameMoveResponse placeStone(String roomCode, Long userId, int row, int col) {
        Game game = loadGamePort.findByRoomCode(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.GAME_NOT_FOUND));

        if (!game.isInProgress()) {
            throw new OmokException(ErrorCode.GAME_NOT_IN_PROGRESS);
        }

        if (!game.isParticipant(userId)) {
            throw new OmokException(ErrorCode.NOT_GAME_PARTICIPANT);
        }

        StoneColor playerColor = game.getPlayerColor(userId);

        if (game.getCurrentTurn() != playerColor) {
            throw new OmokException(ErrorCode.GAME_NOT_YOUR_TURN);
        }

        List<GameMove> existingMoves = loadGameMovesPort.findByGameIdOrderByMoveNumberAsc(game.getId());
        StoneColor[][] board = gameEngine.buildBoardFromMoves(existingMoves);

        if (board[row][col] != null) {
            throw new OmokException(ErrorCode.POSITION_ALREADY_OCCUPIED);
        }

        int moveNumber = existingMoves.size() + 1;
        User player = findUserById(userId);
        GameMove move = GameMove.of(game, player, playerColor, row, col, moveNumber);
        saveGameMovePort.save(move);

        board[row][col] = playerColor;

        if (gameEngine.checkWin(board, row, col)) {
            game.finish(player);
            updatePlayerStats(game, player);
        } else if (gameEngine.isBoardFull(board)) {
            game.draw();
            recordDraw(game);
        } else {
            game.switchTurn();
        }

        return GameMoveResponse.from(move);
    }

    @Override
    @Transactional
    public GameRoomResponse surrender(String roomCode, Long userId) {
        Game game = loadGamePort.findByRoomCode(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.GAME_NOT_FOUND));

        if (!game.isInProgress()) {
            throw new OmokException(ErrorCode.GAME_NOT_IN_PROGRESS);
        }

        if (!game.isParticipant(userId)) {
            throw new OmokException(ErrorCode.NOT_GAME_PARTICIPANT);
        }

        StoneColor loserColor = game.getPlayerColor(userId);
        User winner = (loserColor == StoneColor.BLACK) ? game.getWhitePlayer() : game.getBlackPlayer();

        game.finish(winner);
        updatePlayerStats(game, winner);

        return GameRoomResponse.from(game);
    }

    @Override
    public GameRoomResponse getGame(String roomCode) {
        Game game = loadGamePort.findByRoomCode(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.GAME_NOT_FOUND));
        return GameRoomResponse.from(game);
    }

    @Override
    public Page<GameRoomResponse> getWaitingGames(Pageable pageable) {
        return loadGamePort.findByStatus(GameStatus.WAITING, pageable)
                .map(GameRoomResponse::from);
    }

    @Override
    public Page<GameRoomResponse> getMyGames(Long userId, Pageable pageable) {
        return loadGamePort.findByUserId(userId, pageable)
                .map(GameRoomResponse::from);
    }

    @Override
    public List<GameMoveResponse> getGameMoves(String roomCode) {
        Game game = loadGamePort.findByRoomCode(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.GAME_NOT_FOUND));
        return loadGameMovesPort.findByGameIdOrderByMoveNumberAsc(game.getId())
                .stream()
                .map(GameMoveResponse::from)
                .toList();
    }

    private void updatePlayerStats(Game game, User winner) {
        User loser = winner.getId().equals(game.getBlackPlayer().getId())
                ? game.getWhitePlayer()
                : game.getBlackPlayer();
        winner.recordWin();
        loser.recordLoss();
    }

    private void recordDraw(Game game) {
        game.getBlackPlayer().recordDraw();
        game.getWhitePlayer().recordDraw();
    }

    private String generateUniqueRoomCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (loadGamePort.existsByRoomCode(code));
        return code;
    }

    private User findUserById(Long userId) {
        return loadUserPort.findById(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
    }
}
