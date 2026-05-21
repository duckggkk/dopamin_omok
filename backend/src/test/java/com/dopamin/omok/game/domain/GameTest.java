package com.dopamin.omok.game.domain;

import com.dopamin.omok.support.UserFixture;
import com.dopamin.omok.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameTest {

    @Test
    @DisplayName("방 생성 시 WAITING 상태로 시작")
    void createRoom_isWaiting() {
        User creator = UserFixture.create(1L);
        Game game = Game.createRoom(creator, "ROOM1234");

        assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING);
        assertThat(game.getBlackPlayer()).isEqualTo(creator);
        assertThat(game.getWhitePlayer()).isNull();
        assertThat(game.getRoomCode()).isEqualTo("ROOM1234");
        assertThat(game.getBoardSize()).isEqualTo(15);
    }

    @Test
    @DisplayName("백돌 참가 시 IN_PROGRESS 상태, 흑돌 선공")
    void joinAsWhitePlayer_startsGame() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = Game.createRoom(black, "ROOM1234");

        game.joinAsWhitePlayer(white);

        assertThat(game.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(game.getWhitePlayer()).isEqualTo(white);
        assertThat(game.getCurrentTurn()).isEqualTo(StoneColor.BLACK);
        assertThat(game.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("턴이 교대됨")
    void switchTurn() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = Game.createRoom(black, "ROOM1234");
        game.joinAsWhitePlayer(white);

        assertThat(game.getCurrentTurn()).isEqualTo(StoneColor.BLACK);
        game.switchTurn();
        assertThat(game.getCurrentTurn()).isEqualTo(StoneColor.WHITE);
        game.switchTurn();
        assertThat(game.getCurrentTurn()).isEqualTo(StoneColor.BLACK);
    }

    @Test
    @DisplayName("게임 종료 시 승자와 FINISHED 상태 기록")
    void finish_setsWinnerAndStatus() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = Game.createRoom(black, "ROOM1234");
        game.joinAsWhitePlayer(white);

        game.finish(black);

        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getWinner()).isEqualTo(black);
        assertThat(game.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("무승부 시 DRAW 상태")
    void draw_setsDrawStatus() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = Game.createRoom(black, "ROOM1234");
        game.joinAsWhitePlayer(white);

        game.draw();

        assertThat(game.getStatus()).isEqualTo(GameStatus.DRAW);
        assertThat(game.getWinner()).isNull();
        assertThat(game.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("isParticipant — 참가자는 true, 비참가자는 false")
    void isParticipant() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        User outsider = UserFixture.create(3L);
        Game game = Game.createRoom(black, "ROOM1234");
        game.joinAsWhitePlayer(white);

        assertThat(game.isParticipant(black.getId())).isTrue();
        assertThat(game.isParticipant(white.getId())).isTrue();
        assertThat(game.isParticipant(outsider.getId())).isFalse();
    }

    @Test
    @DisplayName("getPlayerColor — 정확한 색 반환")
    void getPlayerColor() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = Game.createRoom(black, "ROOM1234");
        game.joinAsWhitePlayer(white);

        assertThat(game.getPlayerColor(black.getId())).isEqualTo(StoneColor.BLACK);
        assertThat(game.getPlayerColor(white.getId())).isEqualTo(StoneColor.WHITE);
        assertThat(game.getPlayerColor(99L)).isNull();
    }

    @Test
    @DisplayName("isWaiting, isInProgress 상태 판별")
    void statusChecks() {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        Game game = Game.createRoom(black, "ROOM1234");

        assertThat(game.isWaiting()).isTrue();
        assertThat(game.isInProgress()).isFalse();

        game.joinAsWhitePlayer(white);

        assertThat(game.isWaiting()).isFalse();
        assertThat(game.isInProgress()).isTrue();
    }
}
