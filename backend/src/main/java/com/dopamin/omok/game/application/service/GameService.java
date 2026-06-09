package com.dopamin.omok.game.application.service;

import com.dopamin.omok.game.application.dto.GameMoveResponse;
import com.dopamin.omok.game.application.dto.GameResponse;
import com.dopamin.omok.game.application.port.in.*;
import com.dopamin.omok.game.application.port.out.*;
import com.dopamin.omok.game.domain.*;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService implements PlaceStoneUseCase, SurrenderUseCase,
        GetGameUseCase, GetGameMovesUseCase, GetMyGamesUseCase {

    private final LoadRoomPort loadRoomPort;
    private final SaveRoomPort saveRoomPort;
    private final LoadGamePort loadGamePort;
    private final SaveGamePort saveGamePort;
    private final LoadGameMovesPort loadGameMovesPort;
    private final SaveGameMovePort saveGameMovePort;
    private final LoadGamePlayerPort loadGamePlayerPort;
    private final SaveGamePlayerPort saveGamePlayerPort;
    private final LoadUserPort loadUserPort;
    private final LoadStoneSoundPort loadStoneSoundPort;
    private final OmokGameEngine gameEngine;

    @Override
    @Transactional
    public GameMoveResponse placeStone(String roomCode, Long userId, int row, int col) {
        Game game = findActiveGame(roomCode);

        if (!game.isParticipant(userId)) {
            throw new OmokException(ErrorCode.NOT_GAME_PARTICIPANT);
        }

        StoneColor playerColor = game.getPlayerColor(userId);
        if (game.getCurrentTurn() != playerColor) {
            throw new OmokException(ErrorCode.GAME_NOT_YOUR_TURN);
        }

        // 시간 제한 체크
        checkAndDeductTime(game, userId, roomCode);

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
            updateWinLoss(game, player);
        } else if (gameEngine.isBoardFull(board)) {
            game.draw();
            recordDraw(game);
        } else {
            game.switchTurn();
        }
        saveGamePort.save(game);

        if (!game.isInProgress()) {
            loadRoomPort.findByRoomCode(roomCode).ifPresent(room -> {
                room.waitForNextGame();
                saveRoomPort.save(room);
            });
        }

        // 둔 사람의 장착 착수음을 응답에 실어 브로드캐스트 → 상대/관전자도 같은 소리를 듣는다.
        String soundAssetKey = loadStoneSoundPort.findEquippedStoneSoundKey(userId).orElse(null);
        return GameMoveResponse.from(move, soundAssetKey);
    }

    @Override
    @Transactional
    public GameResponse surrender(String roomCode, Long userId) {
        Game game = findActiveGame(roomCode);

        if (!game.isParticipant(userId)) {
            throw new OmokException(ErrorCode.NOT_GAME_PARTICIPANT);
        }

        StoneColor loserColor = game.getPlayerColor(userId);
        User winner = (loserColor == StoneColor.BLACK) ? game.getWhitePlayer() : game.getBlackPlayer();

        game.finish(winner);
        updateWinLoss(game, winner);
        saveGamePort.save(game);

        loadRoomPort.findByRoomCode(roomCode).ifPresent(room -> {
            room.waitForNextGame();
        });

        return GameResponse.from(game);
    }

    @Override
    public GameResponse getGame(String roomCode) {
        Game game = findActiveGame(roomCode);
        return GameResponse.from(game);
    }

    @Override
    public List<GameMoveResponse> getGameMoves(String roomCode) {
        Game game = findActiveGame(roomCode);
        return loadGameMovesPort.findByGameIdOrderByMoveNumberAsc(game.getId())
                .stream()
                .map(GameMoveResponse::from)
                .toList();
    }

    @Override
    public Page<GameResponse> getMyGames(Long userId, Pageable pageable) {
        return loadGamePort.findCompletedByUserId(userId, pageable)
                .map(GameResponse::from);
    }

    private void checkAndDeductTime(Game game, Long userId, String roomCode) {
        Room room = loadRoomPort.findByRoomCode(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.ROOM_NOT_FOUND));

        if (room.getTimeLimit().isUnlimited()) return;

        GamePlayer gamePlayer = loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new OmokException(ErrorCode.NOT_GAME_PARTICIPANT));

        LocalDateTime lastMove = game.getLastMoveAt();
        long elapsed = ChronoUnit.SECONDS.between(lastMove, LocalDateTime.now());

        boolean timedOut = gamePlayer.deductTimeAndCheckTimeout(elapsed, room.getByoyomiOption());
        saveGamePlayerPort.save(gamePlayer);

        if (timedOut) {
            User winner = game.getOpponent(userId);
            game.finish(winner);
            updateWinLoss(game, winner);
            saveGamePort.save(game);
            throw new OmokException(ErrorCode.PLAYER_TIMEOUT);
        }
    }

    private Game findActiveGame(String roomCode) {
        return loadGamePort.findActiveGameByRoomCode(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.GAME_NOT_FOUND));
    }

    private void updateWinLoss(Game game, User winner) {
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

    private User findUserById(Long userId) {
        return loadUserPort.findById(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
    }
}
