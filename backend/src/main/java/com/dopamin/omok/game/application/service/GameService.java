package com.dopamin.omok.game.application.service;

import com.dopamin.omok.game.application.dto.GameMoveResponse;
import com.dopamin.omok.game.application.dto.GameResponse;
import com.dopamin.omok.game.application.port.in.*;
import com.dopamin.omok.game.application.port.out.*;
import com.dopamin.omok.game.domain.*;
import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;
import com.dopamin.omok.game.physical.application.port.out.LoadPhysicalReplayPort;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.domain.EloRating;
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
        GetGameUseCase, GetGameMovesUseCase, GetMyGamesUseCase, GetPhysicalReplayUseCase {

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
    private final LoadPhysicalReplayPort loadPhysicalReplayPort;
    private final OmokGameEngine gameEngine;
    private final RenjuRuleEngine renjuRuleEngine;

    @Override
    @Transactional
    public GameMoveResponse placeStone(String roomCode, Long userId, int row, int col) {
        Game game = findActiveGame(roomCode);
        Room room = loadRoomPort.findByRoomCode(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.ROOM_NOT_FOUND));

        if (!game.isParticipant(userId)) {
            throw new OmokException(ErrorCode.NOT_GAME_PARTICIPANT);
        }

        StoneColor playerColor = game.getPlayerColor(userId);
        if (game.getCurrentTurn() != playerColor) {
            throw new OmokException(ErrorCode.GAME_NOT_YOUR_TURN);
        }

        // 시간 제한 체크
        checkAndDeductTime(room, game, userId);

        List<GameMove> existingMoves = loadGameMovesPort.findByGameIdOrderByMoveNumberAsc(game.getId());
        StoneColor[][] board = gameEngine.buildBoardFromMoves(existingMoves);

        if (board[row][col] != null) {
            throw new OmokException(ErrorCode.POSITION_ALREADY_OCCUPIED);
        }

        // 둔 수를 보드에 반영하고 승패/금수를 판정(아직 영속화 전).
        board[row][col] = playerColor;
        boolean win = gameEngine.checkWin(board, row, col);

        // 렌주룰: 흑의 금수(3-3·4-4·장목)면 착수 거부. 단, 5목 완성(승리)이면 허용.
        if (room.isRenju() && playerColor == StoneColor.BLACK && !win
                && renjuRuleEngine.isForbidden(board, row, col)) {
            throw new OmokException(ErrorCode.RENJU_FORBIDDEN_MOVE);
        }

        int moveNumber = existingMoves.size() + 1;
        User player = findUserById(userId);
        GameMove move = GameMove.of(game, player, playerColor, row, col, moveNumber);
        saveGameMovePort.save(move);

        if (win) {
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
            room.waitForNextGame();
            saveRoomPort.save(room);
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
    public List<GameMoveResponse> getGameMovesByGameId(Long gameId, Long userId) {
        Game game = loadGamePort.findById(gameId)
                .orElseThrow(() -> new OmokException(ErrorCode.GAME_NOT_FOUND));
        if (!game.isParticipant(userId)) {
            throw new OmokException(ErrorCode.NOT_GAME_PARTICIPANT);
        }
        return loadGameMovesPort.findByGameIdOrderByMoveNumberAsc(gameId)
                .stream()
                .map(GameMoveResponse::from)
                .toList();
    }

    @Override
    public PhysicalReplayData getReplay(Long gameId, Long userId) {
        Game game = loadGamePort.findById(gameId)
                .orElseThrow(() -> new OmokException(ErrorCode.GAME_NOT_FOUND));
        if (!game.isParticipant(userId)) {
            throw new OmokException(ErrorCode.NOT_GAME_PARTICIPANT);
        }
        return loadPhysicalReplayPort.findByGameId(gameId).orElse(null);
    }

    @Override
    public Page<GameResponse> getMyGames(Long userId, Pageable pageable) {
        return loadGamePort.findCompletedByUserId(userId, pageable)
                .map(GameResponse::from);
    }

    private void checkAndDeductTime(Room room, Game game, Long userId) {
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
        // GameService(착수/기권/시간초과)는 일반 오목 전용 경로 → 일반 레이팅만 갱신.
        EloRating.applyResult(winner, loser, false);
    }

    private void recordDraw(Game game) {
        User black = game.getBlackPlayer();
        User white = game.getWhitePlayer();
        black.recordDraw();
        white.recordDraw();
        EloRating.applyDraw(black, white, false);
    }

    private User findUserById(Long userId) {
        return loadUserPort.findById(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
    }
}
