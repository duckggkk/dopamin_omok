package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameMove;
import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.user.adapter.out.persistence.UserJpaRepository;
import com.dopamin.omok.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.dopamin.omok.config.JpaAuditingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class GamePersistenceAdapterTest {

    @Autowired GameJpaRepository gameJpaRepository;
    @Autowired GameMoveJpaRepository gameMoveJpaRepository;
    @Autowired UserJpaRepository userJpaRepository;

    private GamePersistenceAdapter gameAdapter;
    private GameMovePersistenceAdapter gameMoveAdapter;

    private User black;
    private User white;

    @BeforeEach
    void setUp() {
        gameAdapter = new GamePersistenceAdapter(gameJpaRepository);
        gameMoveAdapter = new GameMovePersistenceAdapter(gameMoveJpaRepository);

        black = userJpaRepository.save(User.createLocalUser("black@test.com", "pass1!", "black"));
        white = userJpaRepository.save(User.createLocalUser("white@test.com", "pass1!", "white"));
    }

    private Game savedWaitingGame(String roomCode) {
        return gameAdapter.save(Game.createRoom(black, roomCode));
    }

    private Game savedInProgressGame(String roomCode) {
        Game game = savedWaitingGame(roomCode);
        game.joinAsWhitePlayer(white);
        return gameAdapter.save(game);
    }

    @Test
    @DisplayName("게임 저장 후 roomCode로 조회")
    void save_and_findByRoomCode() {
        savedWaitingGame("ROOM01");

        Optional<Game> found = gameAdapter.findByRoomCode("ROOM01");

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(GameStatus.WAITING);
    }

    @Test
    @DisplayName("존재하지 않는 roomCode는 빈 Optional")
    void findByRoomCode_notFound() {
        assertThat(gameAdapter.findByRoomCode("NONE")).isEmpty();
    }

    @Test
    @DisplayName("roomCode 존재 여부 확인")
    void existsByRoomCode() {
        savedWaitingGame("ROOM02");

        assertThat(gameAdapter.existsByRoomCode("ROOM02")).isTrue();
        assertThat(gameAdapter.existsByRoomCode("OTHER")).isFalse();
    }

    @Test
    @DisplayName("ID로 게임 조회")
    void findById() {
        Game saved = savedWaitingGame("ROOM03");

        Optional<Game> found = gameAdapter.findById(saved.getId());

        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("상태별 게임 페이지 조회")
    void findByStatus_waiting() {
        savedWaitingGame("ROOM04");
        savedWaitingGame("ROOM05");
        savedInProgressGame("ROOM06");

        Page<Game> page = gameAdapter.findByStatus(GameStatus.WAITING, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(g -> g.getStatus() == GameStatus.WAITING);
    }

    @Test
    @DisplayName("userId로 참가 게임 페이지 조회")
    void findByUserId() {
        savedInProgressGame("ROOM07");
        savedWaitingGame("ROOM08");  // only black is participant

        Page<Game> page = gameAdapter.findByUserId(black.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("게임 이동 저장 후 gameId로 순서대로 조회")
    void saveMove_and_findByGameIdOrderByMoveNumberAsc() {
        Game game = savedInProgressGame("ROOM09");

        gameMoveAdapter.save(GameMove.of(game, black, StoneColor.BLACK, 7, 7, 1));
        gameMoveAdapter.save(GameMove.of(game, white, StoneColor.WHITE, 8, 8, 2));
        gameMoveAdapter.save(GameMove.of(game, black, StoneColor.BLACK, 7, 8, 3));

        List<GameMove> moves = gameMoveAdapter.findByGameIdOrderByMoveNumberAsc(game.getId());

        assertThat(moves).hasSize(3);
        assertThat(moves.get(0).getMoveNumber()).isEqualTo(1);
        assertThat(moves.get(1).getMoveNumber()).isEqualTo(2);
        assertThat(moves.get(2).getMoveNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("이동이 없는 게임은 빈 목록 반환")
    void findMoves_emptyGame() {
        Game game = savedInProgressGame("ROOM10");

        List<GameMove> moves = gameMoveAdapter.findByGameIdOrderByMoveNumberAsc(game.getId());

        assertThat(moves).isEmpty();
    }
}
