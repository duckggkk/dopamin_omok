package com.dopamin.omok.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenjuRuleEngineTest {

    private static final int SIZE = 15;
    private final RenjuRuleEngine renju = new RenjuRuleEngine();

    private StoneColor[][] emptyBoard() {
        return new StoneColor[SIZE][SIZE];
    }

    /** 흑 돌 배치 헬퍼. 마지막 인자 (r,c)를 '방금 둔 수'로 두고 금수 여부를 검사한다. */
    private boolean place(StoneColor[][] board, int r, int c) {
        board[r][c] = StoneColor.BLACK;
        return renju.isForbidden(board, r, c);
    }

    @Test
    @DisplayName("3-3(열린 삼 둘)은 흑 금수")
    void doubleThreeIsForbidden() {
        StoneColor[][] board = emptyBoard();
        board[7][6] = StoneColor.BLACK;
        board[7][8] = StoneColor.BLACK; // 가로 삼 재료
        board[6][7] = StoneColor.BLACK;
        board[8][7] = StoneColor.BLACK; // 세로 삼 재료
        assertThat(place(board, 7, 7)).isTrue();
    }

    @Test
    @DisplayName("열린 삼 하나만이면 금수가 아니다")
    void singleThreeIsAllowed() {
        StoneColor[][] board = emptyBoard();
        board[7][6] = StoneColor.BLACK;
        board[7][8] = StoneColor.BLACK;
        assertThat(place(board, 7, 7)).isFalse();
    }

    @Test
    @DisplayName("4-4(사 둘)는 흑 금수")
    void doubleFourIsForbidden() {
        StoneColor[][] board = emptyBoard();
        board[7][4] = StoneColor.BLACK;
        board[7][5] = StoneColor.BLACK;
        board[7][6] = StoneColor.BLACK; // 가로 사 재료
        board[4][7] = StoneColor.BLACK;
        board[5][7] = StoneColor.BLACK;
        board[6][7] = StoneColor.BLACK; // 세로 사 재료
        assertThat(place(board, 7, 7)).isTrue();
    }

    @Test
    @DisplayName("4-3(사 하나 + 삼 하나)은 금수가 아니다")
    void fourThreeIsAllowed() {
        StoneColor[][] board = emptyBoard();
        board[7][4] = StoneColor.BLACK;
        board[7][5] = StoneColor.BLACK;
        board[7][6] = StoneColor.BLACK; // 가로 사
        board[6][7] = StoneColor.BLACK;
        board[8][7] = StoneColor.BLACK; // 세로 삼
        assertThat(place(board, 7, 7)).isFalse();
    }

    @Test
    @DisplayName("장목(6목 이상)은 흑 금수")
    void overlineIsForbidden() {
        StoneColor[][] board = emptyBoard();
        board[7][2] = StoneColor.BLACK;
        board[7][3] = StoneColor.BLACK;
        board[7][4] = StoneColor.BLACK;
        board[7][6] = StoneColor.BLACK;
        board[7][7] = StoneColor.BLACK; // 가운데 (7,5)를 채우면 2..7 = 6목
        assertThat(place(board, 7, 5)).isTrue();
    }

    @Test
    @DisplayName("정확히 5목을 만드는 수는 금수가 아니다(승리 우선)")
    void exactFiveIsAllowed() {
        StoneColor[][] board = emptyBoard();
        board[7][3] = StoneColor.BLACK;
        board[7][4] = StoneColor.BLACK;
        board[7][5] = StoneColor.BLACK;
        board[7][6] = StoneColor.BLACK;
        assertThat(place(board, 7, 7)).isFalse(); // 3..7 = 정확히 5목
    }

    @Test
    @DisplayName("백 돌은 금수 판정 대상이 아니다")
    void whiteIsNeverForbidden() {
        StoneColor[][] board = emptyBoard();
        board[7][6] = StoneColor.WHITE;
        board[7][8] = StoneColor.WHITE;
        board[6][7] = StoneColor.WHITE;
        board[8][7] = StoneColor.WHITE;
        board[7][7] = StoneColor.WHITE;
        assertThat(renju.isForbidden(board, 7, 7)).isFalse();
    }
}
