package com.dopamin.omok.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OmokGameEngineTest {

    private static final int SIZE = 15;
    private final OmokGameEngine engine = new OmokGameEngine();

    private StoneColor[][] emptyBoard() {
        return new StoneColor[SIZE][SIZE];
    }

    @Test
    @DisplayName("가로 5연속이면 승리")
    void detectsHorizontalFive() {
        StoneColor[][] board = emptyBoard();
        for (int c = 0; c < 5; c++) board[7][c] = StoneColor.BLACK;
        assertThat(engine.checkWin(board, 7, 4)).isTrue();
    }

    @Test
    @DisplayName("세로 5연속이면 승리")
    void detectsVerticalFive() {
        StoneColor[][] board = emptyBoard();
        for (int r = 0; r < 5; r++) board[r][7] = StoneColor.WHITE;
        assertThat(engine.checkWin(board, 4, 7)).isTrue();
    }

    @Test
    @DisplayName("대각선(\\) 5연속이면 승리")
    void detectsDiagonalFive() {
        StoneColor[][] board = emptyBoard();
        for (int i = 0; i < 5; i++) board[i][i] = StoneColor.BLACK;
        assertThat(engine.checkWin(board, 2, 2)).isTrue();
    }

    @Test
    @DisplayName("역대각선(/) 5연속이면 승리")
    void detectsAntiDiagonalFive() {
        StoneColor[][] board = emptyBoard();
        for (int i = 0; i < 5; i++) board[i][4 - i] = StoneColor.WHITE;
        assertThat(engine.checkWin(board, 2, 2)).isTrue();
    }

    @Test
    @DisplayName("4연속은 승리가 아니다")
    void fourInRowIsNotWin() {
        StoneColor[][] board = emptyBoard();
        for (int c = 0; c < 4; c++) board[7][c] = StoneColor.BLACK;
        assertThat(engine.checkWin(board, 7, 3)).isFalse();
    }

    @Test
    @DisplayName("6목(장목)은 승리가 아니다 — '오목'만 인정")
    void overlineSixIsNotWin() {
        StoneColor[][] board = emptyBoard();
        for (int c = 0; c < 6; c++) board[7][c] = StoneColor.BLACK;
        // 어느 칸 기준으로 판정해도 연속 길이가 6이라 승리가 아니다
        assertThat(engine.checkWin(board, 7, 5)).isFalse();
        assertThat(engine.checkWin(board, 7, 2)).isFalse();
    }

    @Test
    @DisplayName("빈 칸 기준 승리 판정은 false")
    void emptyCellIsNotWin() {
        assertThat(engine.checkWin(emptyBoard(), 7, 7)).isFalse();
    }

    @Test
    @DisplayName("보드가 가득 찼는지 판정")
    void isBoardFull() {
        StoneColor[][] board = emptyBoard();
        assertThat(engine.isBoardFull(board)).isFalse();
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                board[r][c] = StoneColor.BLACK;
        assertThat(engine.isBoardFull(board)).isTrue();
    }

    @Test
    @DisplayName("좌표 유효성 검사")
    void isValidPosition() {
        assertThat(engine.isValidPosition(0, 0)).isTrue();
        assertThat(engine.isValidPosition(14, 14)).isTrue();
        assertThat(engine.isValidPosition(-1, 0)).isFalse();
        assertThat(engine.isValidPosition(0, 15)).isFalse();
    }
}
