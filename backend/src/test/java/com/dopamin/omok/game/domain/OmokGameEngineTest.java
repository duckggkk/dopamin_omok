package com.dopamin.omok.game.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OmokGameEngineTest {

    private OmokGameEngine engine;

    @BeforeEach
    void setUp() {
        engine = new OmokGameEngine();
    }

    // ── checkWin ────────────────────────────────────────────────

    @Test
    @DisplayName("가로 5개 연속이면 승리")
    void checkWin_horizontal() {
        StoneColor[][] board = emptyBoard();
        for (int c = 0; c < 5; c++) board[7][c] = StoneColor.BLACK;

        assertThat(engine.checkWin(board, 7, 4)).isTrue();
    }

    @Test
    @DisplayName("세로 5개 연속이면 승리")
    void checkWin_vertical() {
        StoneColor[][] board = emptyBoard();
        for (int r = 0; r < 5; r++) board[r][7] = StoneColor.WHITE;

        assertThat(engine.checkWin(board, 4, 7)).isTrue();
    }

    @Test
    @DisplayName("우하향 대각선 5개 연속이면 승리")
    void checkWin_diagonalDownRight() {
        StoneColor[][] board = emptyBoard();
        for (int i = 0; i < 5; i++) board[i][i] = StoneColor.BLACK;

        assertThat(engine.checkWin(board, 4, 4)).isTrue();
    }

    @Test
    @DisplayName("좌하향 대각선 5개 연속이면 승리")
    void checkWin_diagonalDownLeft() {
        StoneColor[][] board = emptyBoard();
        for (int i = 0; i < 5; i++) board[i][4 - i] = StoneColor.BLACK;

        assertThat(engine.checkWin(board, 4, 0)).isTrue();
    }

    @Test
    @DisplayName("4개 연속이면 승리가 아님")
    void checkWin_only4_notWin() {
        StoneColor[][] board = emptyBoard();
        for (int c = 0; c < 4; c++) board[7][c] = StoneColor.BLACK;

        assertThat(engine.checkWin(board, 7, 3)).isFalse();
    }

    @Test
    @DisplayName("null 위치는 승리가 아님")
    void checkWin_nullPosition_notWin() {
        StoneColor[][] board = emptyBoard();

        assertThat(engine.checkWin(board, 7, 7)).isFalse();
    }

    @Test
    @DisplayName("6개 연속도 승리 (렌주룰 미적용)")
    void checkWin_moreThan5_win() {
        StoneColor[][] board = emptyBoard();
        for (int c = 0; c < 6; c++) board[7][c] = StoneColor.BLACK;

        assertThat(engine.checkWin(board, 7, 5)).isTrue();
    }

    // ── isBoardFull ─────────────────────────────────────────────

    @Test
    @DisplayName("빈 보드는 가득 차지 않음")
    void isBoardFull_emptyBoard_false() {
        assertThat(engine.isBoardFull(emptyBoard())).isFalse();
    }

    @Test
    @DisplayName("모든 칸이 채워지면 true")
    void isBoardFull_fullBoard_true() {
        StoneColor[][] board = emptyBoard();
        for (int r = 0; r < 15; r++)
            for (int c = 0; c < 15; c++)
                board[r][c] = (r + c) % 2 == 0 ? StoneColor.BLACK : StoneColor.WHITE;

        assertThat(engine.isBoardFull(board)).isTrue();
    }

    @Test
    @DisplayName("마지막 한 칸이 비어있으면 false")
    void isBoardFull_oneEmpty_false() {
        StoneColor[][] board = emptyBoard();
        for (int r = 0; r < 15; r++)
            for (int c = 0; c < 15; c++)
                board[r][c] = StoneColor.BLACK;
        board[14][14] = null;

        assertThat(engine.isBoardFull(board)).isFalse();
    }

    // ── isValidPosition ──────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({"0,0", "14,14", "7,7", "0,14", "14,0"})
    @DisplayName("보드 범위 내 위치는 유효")
    void isValidPosition_inBounds_true(int row, int col) {
        assertThat(engine.isValidPosition(row, col)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "0,-1", "15,0", "0,15", "15,15"})
    @DisplayName("보드 범위 밖 위치는 무효")
    void isValidPosition_outOfBounds_false(int row, int col) {
        assertThat(engine.isValidPosition(row, col)).isFalse();
    }

    // ── buildBoardFromMoves ──────────────────────────────────────

    @Test
    @DisplayName("이동 목록으로 보드를 정확히 복원")
    void buildBoardFromMoves_correctlyRestoresBoard() {
        StoneColor[][] built = engine.buildBoardFromMoves(List.of());
        for (int r = 0; r < 15; r++)
            for (int c = 0; c < 15; c++)
                assertThat(built[r][c]).isNull();
    }

    private StoneColor[][] emptyBoard() {
        return new StoneColor[15][15];
    }
}
