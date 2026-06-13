package com.dopamin.omok.game.domain;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OmokGameEngine {

    private static final int BOARD_SIZE = 15;
    private static final int WIN_COUNT = 5;

    private static final int[][] DIRECTIONS = {
            {0, 1},
            {1, 0},
            {1, 1},
            {1, -1}
    };

    public boolean checkWin(StoneColor[][] board, int row, int col) {
        StoneColor color = board[row][col];
        if (color == null) return false;

        for (int[] dir : DIRECTIONS) {
            int count = 1;
            count += countInDirection(board, row, col, dir[0], dir[1], color);
            count += countInDirection(board, row, col, -dir[0], -dir[1], color);

            // '오목'만 승리 — 정확히 5목일 때만. 6목 이상(장목/오버라인)은 승리로 인정하지 않는다.
            if (count == WIN_COUNT) {
                return true;
            }
        }
        return false;
    }

    public boolean isBoardFull(StoneColor[][] board) {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] == null) return false;
            }
        }
        return true;
    }

    public boolean isValidPosition(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    public StoneColor[][] buildBoardFromMoves(List<GameMove> moves) {
        StoneColor[][] board = new StoneColor[BOARD_SIZE][BOARD_SIZE];
        for (GameMove move : moves) {
            board[move.getRow()][move.getCol()] = move.getColor();
        }
        return board;
    }

    private int countInDirection(StoneColor[][] board, int row, int col,
                                  int dRow, int dCol, StoneColor color) {
        int count = 0;
        int r = row + dRow;
        int c = col + dCol;

        while (isValidPosition(r, c) && board[r][c] == color) {
            count++;
            r += dRow;
            c += dCol;
        }
        return count;
    }
}
