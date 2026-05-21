package com.dopamin.omok.game.application.service;

import com.dopamin.omok.game.application.dto.GameMoveResponse;
import com.dopamin.omok.game.application.dto.GameRoomResponse;
import com.dopamin.omok.game.application.port.out.LoadGameMovesPort;
import com.dopamin.omok.game.application.port.out.LoadGamePort;
import com.dopamin.omok.game.application.port.out.SaveGameMovePort;
import com.dopamin.omok.game.application.port.out.SaveGamePort;
import com.dopamin.omok.game.domain.*;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.support.GameFixture;
import com.dopamin.omok.support.UserFixture;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock LoadGamePort loadGamePort;
    @Mock SaveGamePort saveGamePort;
    @Mock LoadGameMovesPort loadGameMovesPort;
    @Mock SaveGameMovePort saveGameMovePort;
    @Mock LoadUserPort loadUserPort;
    @Mock OmokGameEngine gameEngine;
    @InjectMocks GameService gameService;

    // ── createRoom ───────────────────────────────────────────────

    @Test
    @DisplayName("방 생성 성공")
    void createRoom_success() {
        User user = UserFixture.create(1L);
        given(loadUserPort.findById(1L)).willReturn(Optional.of(user));
        given(loadGamePort.existsByRoomCode(anyString())).willReturn(false);
        given(saveGamePort.save(any(Game.class))).willAnswer(inv -> inv.getArgument(0));

        GameRoomResponse result = gameService.createRoom(1L);

        assertThat(result.status()).isEqualTo(GameStatus.WAITING);
        assertThat(result.blackPlayer().nickname()).isEqualTo(user.getNickname());
    }

    // ── joinRoom ─────────────────────────────────────────────────

    @Test
    @DisplayName("방 참가 성공 시 IN_PROGRESS 상태")
    void joinRoom_success_inProgress() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = GameFixture.createWaiting(black);
        given(loadGamePort.findByRoomCode("ROOM1")).willReturn(Optional.of(game));
        given(loadUserPort.findById(2L)).willReturn(Optional.of(white));

        GameRoomResponse result = gameService.joinRoom("ROOM1", 2L);

        assertThat(result.status()).isEqualTo(GameStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("이미 참가 중인 방에 재입장 시 현재 상태 반환")
    void joinRoom_alreadyParticipant_returnsCurrentState() {
        User black = UserFixture.create(1L);
        Game game = GameFixture.createWaiting(black);
        given(loadGamePort.findByRoomCode("ROOM1")).willReturn(Optional.of(game));

        GameRoomResponse result = gameService.joinRoom("ROOM1", 1L);

        assertThat(result.status()).isEqualTo(GameStatus.WAITING);
        then(loadUserPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("대기 중이 아닌 방에 입장 시 예외")
    void joinRoom_notWaiting_throwsException() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = GameFixture.createInProgress(black, white);
        given(loadGamePort.findByRoomCode("ROOM1")).willReturn(Optional.of(game));

        assertThatThrownBy(() -> gameService.joinRoom("ROOM1", 3L))
                .isInstanceOf(OmokException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GAME_ALREADY_FULL);
    }

    // ── placeStone ───────────────────────────────────────────────

    @Test
    @DisplayName("돌 놓기 성공")
    void placeStone_success() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = GameFixture.createInProgress(black, white);
        StoneColor[][] board = new StoneColor[15][15];

        given(loadGamePort.findByRoomCode("ROOM1")).willReturn(Optional.of(game));
        given(loadGameMovesPort.findByGameIdOrderByMoveNumberAsc(game.getId())).willReturn(List.of());
        given(gameEngine.buildBoardFromMoves(List.of())).willReturn(board);
        given(gameEngine.checkWin(board, 7, 7)).willReturn(false);
        given(gameEngine.isBoardFull(board)).willReturn(false);
        given(loadUserPort.findById(1L)).willReturn(Optional.of(black));
        given(saveGameMovePort.save(any(GameMove.class))).willReturn(
                GameFixture.createMove(game, black, StoneColor.BLACK, 7, 7, 1));

        GameMoveResponse result = gameService.placeStone("ROOM1", 1L, 7, 7);

        assertThat(result.color()).isEqualTo(StoneColor.BLACK);
        assertThat(result.row()).isEqualTo(7);
        assertThat(result.col()).isEqualTo(7);
    }

    @Test
    @DisplayName("본인 차례가 아닌 경우 예외")
    void placeStone_notYourTurn_throwsException() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = GameFixture.createInProgress(black, white);
        // 현재 흑돌 차례인데 백돌(userId=2)이 두려 함
        given(loadGamePort.findByRoomCode("ROOM1")).willReturn(Optional.of(game));

        assertThatThrownBy(() -> gameService.placeStone("ROOM1", 2L, 7, 7))
                .isInstanceOf(OmokException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GAME_NOT_YOUR_TURN);
    }

    @Test
    @DisplayName("이미 돌이 놓인 위치에 두면 예외")
    void placeStone_occupiedPosition_throwsException() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = GameFixture.createInProgress(black, white);
        StoneColor[][] board = new StoneColor[15][15];
        board[7][7] = StoneColor.WHITE;

        given(loadGamePort.findByRoomCode("ROOM1")).willReturn(Optional.of(game));
        given(loadGameMovesPort.findByGameIdOrderByMoveNumberAsc(game.getId())).willReturn(List.of());
        given(gameEngine.buildBoardFromMoves(any())).willReturn(board);

        assertThatThrownBy(() -> gameService.placeStone("ROOM1", 1L, 7, 7))
                .isInstanceOf(OmokException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POSITION_ALREADY_OCCUPIED);
    }

    @Test
    @DisplayName("5목 달성 시 게임 종료 및 승자 스탯 업데이트")
    void placeStone_win_finishesGame() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = GameFixture.createInProgress(black, white);
        StoneColor[][] board = new StoneColor[15][15];

        given(loadGamePort.findByRoomCode("ROOM1")).willReturn(Optional.of(game));
        given(loadGameMovesPort.findByGameIdOrderByMoveNumberAsc(game.getId())).willReturn(List.of());
        given(gameEngine.buildBoardFromMoves(any())).willReturn(board);
        given(gameEngine.checkWin(board, 7, 7)).willReturn(true);
        given(loadUserPort.findById(1L)).willReturn(Optional.of(black));
        given(saveGameMovePort.save(any())).willAnswer(inv ->
                GameFixture.createMove(game, black, StoneColor.BLACK, 7, 7, 1));

        gameService.placeStone("ROOM1", 1L, 7, 7);

        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getWinner()).isEqualTo(black);
        assertThat(black.getWins()).isEqualTo(1);
        assertThat(white.getLosses()).isEqualTo(1);
    }

    @Test
    @DisplayName("보드가 가득 차면 무승부")
    void placeStone_boardFull_draw() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = GameFixture.createInProgress(black, white);
        StoneColor[][] board = new StoneColor[15][15];

        given(loadGamePort.findByRoomCode("ROOM1")).willReturn(Optional.of(game));
        given(loadGameMovesPort.findByGameIdOrderByMoveNumberAsc(game.getId())).willReturn(List.of());
        given(gameEngine.buildBoardFromMoves(any())).willReturn(board);
        given(gameEngine.checkWin(board, 7, 7)).willReturn(false);
        given(gameEngine.isBoardFull(board)).willReturn(true);
        given(loadUserPort.findById(1L)).willReturn(Optional.of(black));
        given(saveGameMovePort.save(any())).willAnswer(inv ->
                GameFixture.createMove(game, black, StoneColor.BLACK, 7, 7, 1));

        gameService.placeStone("ROOM1", 1L, 7, 7);

        assertThat(game.getStatus()).isEqualTo(GameStatus.DRAW);
        assertThat(black.getDraws()).isEqualTo(1);
        assertThat(white.getDraws()).isEqualTo(1);
    }

    // ── surrender ────────────────────────────────────────────────

    @Test
    @DisplayName("기권 시 상대방이 승리")
    void surrender_success() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = GameFixture.createInProgress(black, white);
        given(loadGamePort.findByRoomCode("ROOM1")).willReturn(Optional.of(game));

        GameRoomResponse result = gameService.surrender("ROOM1", 1L);  // 흑돌 기권

        assertThat(result.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(result.winner().nickname()).isEqualTo(white.getNickname());
    }

    @Test
    @DisplayName("진행 중이 아닌 게임에서 기권 시 예외")
    void surrender_notInProgress_throwsException() {
        User black = UserFixture.create(1L);
        Game game = GameFixture.createWaiting(black);
        given(loadGamePort.findByRoomCode("ROOM1")).willReturn(Optional.of(game));

        assertThatThrownBy(() -> gameService.surrender("ROOM1", 1L))
                .isInstanceOf(OmokException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GAME_NOT_IN_PROGRESS);
    }

    // ── getWaitingGames / getMyGames ─────────────────────────────

    @Test
    @DisplayName("대기 중인 게임 목록 조회")
    void getWaitingGames_returnsPage() {
        User user = UserFixture.create(1L);
        Game game = GameFixture.createWaiting(user);
        PageRequest pageable = PageRequest.of(0, 10);
        given(loadGamePort.findByStatus(GameStatus.WAITING, pageable))
                .willReturn(new PageImpl<>(List.of(game)));

        Page<GameRoomResponse> result = gameService.getWaitingGames(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(GameStatus.WAITING);
    }

    @Test
    @DisplayName("내 게임 목록 조회")
    void getMyGames_returnsPage() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = GameFixture.createInProgress(black, white);
        PageRequest pageable = PageRequest.of(0, 10);
        given(loadGamePort.findByUserId(1L, pageable))
                .willReturn(new PageImpl<>(List.of(game)));

        Page<GameRoomResponse> result = gameService.getMyGames(1L, pageable);

        assertThat(result.getContent()).hasSize(1);
    }
}
