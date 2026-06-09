package com.dopamin.omok.game.domain;

import com.dopamin.omok.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameTest {

    @Mock
    private Room room;
    @Mock
    private User black;
    @Mock
    private User white;

    private Game game;

    @BeforeEach
    void setUp() {
        when(room.getCurrentGameNumber()).thenReturn(1);
        lenient().when(black.getId()).thenReturn(1L);
        lenient().when(white.getId()).thenReturn(2L);
        game = Game.start(room, black, white);
    }

    @Test
    @DisplayName("게임 시작 시 흑 선, 진행 중 상태")
    void initialState() {
        assertThat(game.getCurrentTurn()).isEqualTo(StoneColor.BLACK);
        assertThat(game.isInProgress()).isTrue();
        assertThat(game.getWinner()).isNull();
    }

    @Test
    @DisplayName("턴 전환: 흑 → 백 → 흑")
    void switchTurn() {
        game.switchTurn();
        assertThat(game.getCurrentTurn()).isEqualTo(StoneColor.WHITE);
        game.switchTurn();
        assertThat(game.getCurrentTurn()).isEqualTo(StoneColor.BLACK);
    }

    @Test
    @DisplayName("참가자/색/상대 판정")
    void participantQueries() {
        assertThat(game.isParticipant(1L)).isTrue();
        assertThat(game.isParticipant(99L)).isFalse();

        assertThat(game.getPlayerColor(1L)).isEqualTo(StoneColor.BLACK);
        assertThat(game.getPlayerColor(2L)).isEqualTo(StoneColor.WHITE);
        assertThat(game.getPlayerColor(99L)).isNull();

        assertThat(game.getOpponent(1L)).isEqualTo(white);
        assertThat(game.getOpponent(2L)).isEqualTo(black);
    }

    @Test
    @DisplayName("승리 처리 시 승자/상태 갱신")
    void finishSetsWinner() {
        game.finish(black);
        assertThat(game.getWinner()).isEqualTo(black);
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("무승부 처리")
    void drawSetsStatus() {
        game.draw();
        assertThat(game.getStatus()).isEqualTo(GameStatus.DRAW);
        assertThat(game.isInProgress()).isFalse();
    }
}
