package com.dopamin.omok.game.application.service;

import com.dopamin.omok.game.application.dto.GameMoveResponse;
import com.dopamin.omok.game.application.dto.GameResponse;
import com.dopamin.omok.game.application.dto.GameSummaryResponse;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService implements PlaceStoneUseCase, SurrenderUseCase,
        GetGameMovesUseCase, GetMyGamesUseCase, GetPhysicalReplayUseCase {

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
        // 행 잠금으로 조회한다 — 아래의 "수순 조회 → 중복/턴 검사 → moveNumber 계산 → 저장"
        // 구간이 같은 게임에 대해 동시에 실행되면 안 되기 때문이다.
        // (STOMP clientInboundChannel 은 스레드 풀이라 같은 세션의 두 메시지도 동시 처리될 수 있다)
        Game game = findActiveGameForUpdate(roomCode);
        Room room = loadRoomPort.findByRoomCode(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.ROOM_NOT_FOUND));

        requireClassicGame(game);

        if (!game.isParticipant(userId)) {
            throw new OmokException(ErrorCode.NOT_GAME_PARTICIPANT);
        }

        StoneColor playerColor = game.getPlayerColor(userId);
        if (game.getCurrentTurn() != playerColor) {
            throw new OmokException(ErrorCode.GAME_NOT_YOUR_TURN);
        }

        // 시간 제한 체크
        checkAndDeductTime(room, game, userId);

        // 보드 사이즈와 맞지 않으면
        if (!gameEngine.isValidPosition(row, col)) {
            throw new OmokException(ErrorCode.INVALID_MOVE);
        }

        List<GameMove> existingMoves = loadGameMovesPort.findByGameIdOrderByMoveNumberAsc(game.getId());
        StoneColor[][] board = gameEngine.buildBoardFromMoves(existingMoves);

        //중복착수 방지
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
        // 착수와 동시에 들어오면 승패가 두 번 기록될 수 있으므로 같은 락을 쓴다.
        Game game = findActiveGameForUpdate(roomCode);

        requireClassicGame(game);

        if (!game.isParticipant(userId)) {
            throw new OmokException(ErrorCode.NOT_GAME_PARTICIPANT);
        }

        StoneColor loserColor = game.getPlayerColor(userId);
        User winner = (loserColor == StoneColor.BLACK) ? game.getWhitePlayer() : game.getBlackPlayer();

        game.finish(winner);
        updateWinLoss(game, winner);
        saveGamePort.save(game);

        loadRoomPort.findByRoomCode(roomCode).ifPresent(room -> {
            room.waitForNextGame(); //방대기전환
        });

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
    public List<GameMoveResponse> getPublicGameMovesByGameId(UUID publicId, Long gameId, Long viewerUserId) {
        User owner = findVisibleProfileOwner(publicId, viewerUserId);
        Game game = findVisibleOwnerGame(gameId, owner);
        return loadGameMovesPort.findByGameIdOrderByMoveNumberAsc(game.getId())
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
        // forClient(): 서버 전용 학습 로그를 떼고 내려보낸다.
        return loadPhysicalReplayPort.findByGameId(gameId).map(PhysicalReplayData::forClient).orElse(null);
    }

    @Override
    public PhysicalReplayData getPublicReplay(UUID publicId, Long gameId, Long viewerUserId) {
        User owner = findVisibleProfileOwner(publicId, viewerUserId);
        Game game = findVisibleOwnerGame(gameId, owner);
        return loadPhysicalReplayPort.findByGameId(game.getId()).map(PhysicalReplayData::forClient).orElse(null);
    }

    @Override
    public Page<GameSummaryResponse> getMyGames(Long userId, Pageable pageable) {
        return loadGamePort.findCompletedByUserId(userId, pageable)
                .map(GameSummaryResponse::from);
    }

    @Override
    public Page<GameSummaryResponse> getPublicGames(UUID publicId, Long viewerUserId, Pageable pageable) {
        User owner = findVisibleProfileOwner(publicId, viewerUserId);
        return loadGamePort.findCompletedByUserId(owner.getId(), pageable)
                .map(GameSummaryResponse::from);
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

    /**
     * GameService 의 상태 변경 경로(착수·기권·시간초과)는 클래식 전용이다.
     * 피지컬은 PhysicalGameService 가 자기 세션에서 승패를 정하고 이벤트로 결과를 넘긴다.
     *
     * <p>STOMP 인터셉터는 /app/game/{code}/* 를 '방 멤버인가'로만 통과시키고 방 종류는 보지 않는다.
     * 그래서 피지컬 방 코드로 이 경로를 부르면 두 시스템이 같은 대국을 각자 끝내려 든다.
     * 서버가 직접 막는다.</p>
     */
    private void requireClassicGame(Game game) {
        if (game.getGameType() != GameType.CLASSIC) {
            throw new OmokException(ErrorCode.WRONG_GAME_TYPE_ENDPOINT);
        }
    }

    private Game findActiveGame(String roomCode) {
        return loadGamePort.findActiveGameByRoomCode(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.GAME_NOT_FOUND));
    }

    /**
     * 게임 상태를 바꾸는 경로(착수·기권)용 조회. games 행을 잠근 채로 가져온다.
     * <p>
     * 락을 기다리는 동안 상대가 먼저 이겨서 게임이 끝났다면 IN_PROGRESS 조건에 걸려
     * 결과가 비어 있으므로, 그대로 GAME_NOT_FOUND 로 거절된다.
     */
    private Game findActiveGameForUpdate(String roomCode) {
        return loadGamePort.findActiveGameByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.GAME_NOT_FOUND));
    }

    private User findVisibleProfileOwner(UUID publicId, Long viewerUserId) {
        User owner = loadUserPort.findByPublicId(publicId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        if (owner.isProfilePrivate() && !owner.getId().equals(viewerUserId)) {
            throw new OmokException(ErrorCode.PROFILE_PRIVATE);
        }
        return owner;
    }

    private Game findVisibleOwnerGame(Long gameId, User owner) {
        Game game = loadGamePort.findById(gameId)
                .orElseThrow(() -> new OmokException(ErrorCode.GAME_NOT_FOUND));
        if (!game.isParticipant(owner.getId()) || game.isInProgress()) {
            throw new OmokException(ErrorCode.GAME_NOT_FOUND);
        }
        return game;
    }

    private void updateWinLoss(Game game, User winner) {
        if (!game.isRated()) return; // 봇·게스트 대국: 기록만 남기고 레이팅·전적 미반영
        User loser = winner.getId().equals(game.getBlackPlayer().getId())
                ? game.getWhitePlayer()
                : game.getBlackPlayer();
        winner.recordWin();
        loser.recordLoss();
        EloRating.applyResult(winner, loser, game.getGameType());
    }

    private void recordDraw(Game game) {
        if (!game.isRated()) return; // 봇·게스트 대국: 미반영
        User black = game.getBlackPlayer();
        User white = game.getWhitePlayer();
        black.recordDraw();
        white.recordDraw();
        EloRating.applyDraw(black, white, game.getGameType());
    }

    private User findUserById(Long userId) {
        return loadUserPort.findById(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
    }
}
