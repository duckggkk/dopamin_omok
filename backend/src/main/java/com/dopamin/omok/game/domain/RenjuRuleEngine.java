package com.dopamin.omok.game.domain;

import org.springframework.stereotype.Component;

/**
 * 렌주룰 금수(禁手) 판정 — 흑(선)에게만 적용한다. 백은 제한이 없다.
 *
 * <p>금수 종류:
 * <ul>
 *   <li><b>장목</b> — 한 방향으로 6목 이상(오버라인)이 되는 수.</li>
 *   <li><b>4-4</b> — 한 수로 '사(四)'가 둘 이상 동시에 생기는 수.</li>
 *   <li><b>3-3</b> — 한 수로 '열린 삼(활삼)'이 둘 이상 동시에 생기는 수.</li>
 * </ul>
 * 단, 정확히 5목을 완성하는 수는 항상 합법(승리)이며 금수보다 우선한다.
 *
 * <p>판정 방식: 후보 수는 이미 {@code board[row][col] = BLACK} 로 놓인 상태로 들어온다.
 * 각 방향(가로·세로·두 대각)을 중심 기준 -5..+5(길이 11) 윈도우로 떠서, 빈칸을 하나 더 채워 보며
 * '사(→정확히 5)'·'활삼(→열린 사)'을 만드는지로 분류한다. 5/사/삼 판정에 이 범위로 충분하다.
 *
 * <p>구현 한계(의도적): 한 직선 안에 사/삼이 둘 이상 겹치는 매우 드문 형태는 방향당 1개로 셈하며,
 * 3-3 판정의 깊은 재귀(금수점을 경유해야만 성립하는 삼 배제)는 1단계까지만 본다. 실전에서는 충분하다.
 */
@Component
public class RenjuRuleEngine {

    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
    private static final int BOARD_SIZE = 15;
    private static final int HALF = 5;                 // 중심 기준 좌우 범위
    private static final int CENTER = HALF;            // 윈도우 내 중심 인덱스(=5)
    private static final int WINDOW = HALF * 2 + 1;    // 11

    // 윈도우 셀 상태
    private static final int EMPTY = 0;
    private static final int SELF = 1;   // 흑(자기 돌)
    private static final int BLOCK = 2;  // 백 또는 벽(범위 밖)

    /**
     * 흑이 {@code (row, col)} 에 둔 수가 렌주 금수면 true.
     * 정확히 5목을 만드는 수는 금수가 아니다(승리 우선).
     */
    public boolean isForbidden(StoneColor[][] board, int row, int col) {
        if (board[row][col] != StoneColor.BLACK) return false;

        // 5목(정확히 5)을 만드는 수는 항상 합법.
        if (makesFive(board, row, col)) return false;

        // 장목(6목 이상) → 금수.
        if (hasOverline(board, row, col)) return true;

        int fours = 0;
        int openThrees = 0;
        for (int[] d : DIRECTIONS) {
            int[] line = buildLine(board, row, col, d[0], d[1]);
            if (lineHasFour(line)) fours++;
            if (lineHasOpenThree(line)) openThrees++;
        }
        return fours >= 2 || openThrees >= 2;
    }

    /** 어느 방향이든 중심을 지나는 연속 흑이 정확히 5면 true. */
    private boolean makesFive(StoneColor[][] board, int row, int col) {
        for (int[] d : DIRECTIONS) {
            if (contiguousRun(board, row, col, d[0], d[1]) == 5) return true;
        }
        return false;
    }

    /** 어느 방향이든 중심을 지나는 연속 흑이 6 이상이면 true(장목). */
    private boolean hasOverline(StoneColor[][] board, int row, int col) {
        for (int[] d : DIRECTIONS) {
            if (contiguousRun(board, row, col, d[0], d[1]) >= 6) return true;
        }
        return false;
    }

    /** (row,col)의 흑을 포함해 (dr,dc)/역방향으로 이어지는 연속 흑 개수. */
    private int contiguousRun(StoneColor[][] board, int row, int col, int dr, int dc) {
        return 1 + countDir(board, row, col, dr, dc) + countDir(board, row, col, -dr, -dc);
    }

    private int countDir(StoneColor[][] board, int row, int col, int dr, int dc) {
        int count = 0, r = row + dr, c = col + dc;
        while (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE && board[r][c] == StoneColor.BLACK) {
            count++;
            r += dr;
            c += dc;
        }
        return count;
    }

    /** 한 방향 라인을 -5..+5 윈도우(길이 11)로 떠 온다. 중심은 항상 SELF. */
    private int[] buildLine(StoneColor[][] board, int row, int col, int dr, int dc) {
        int[] line = new int[WINDOW];
        for (int i = 0; i < WINDOW; i++) {
            int off = i - CENTER;
            int r = row + dr * off, c = col + dc * off;
            if (r < 0 || r >= BOARD_SIZE || c < 0 || c >= BOARD_SIZE) line[i] = BLOCK;
            else if (board[r][c] == StoneColor.BLACK) line[i] = SELF;
            else if (board[r][c] == StoneColor.WHITE) line[i] = BLOCK;
            else line[i] = EMPTY;
        }
        return line;
    }

    /** 빈칸 하나(e)를 더 채워 'e를 포함한 중심 연속이 정확히 5목'이 되면 이 방향에 '사'가 있다. */
    private boolean lineHasFour(int[] line) {
        for (int e = 0; e < WINDOW; e++) {
            if (line[e] != EMPTY) continue;
            line[e] = SELF;
            boolean four = madeExactFiveThrough(line, e);
            line[e] = EMPTY;
            if (four) return true;
        }
        return false;
    }

    /** 빈칸 하나(e)를 더 채워 'e를 포함한 열린 사(활사)'가 되면 이 방향에 '활삼'이 있다. */
    private boolean lineHasOpenThree(int[] line) {
        for (int e = 0; e < WINDOW; e++) {
            if (line[e] != EMPTY) continue;
            line[e] = SELF;
            boolean openThree = madeOpenFourThrough(line, e);
            line[e] = EMPTY;
            if (openThree) return true;
        }
        return false;
    }

    /**
     * 방금 채운 칸 e가 '중심을 지나는 연속 SELF'에 포함되고 그 길이가 정확히 5면 true.
     * (e가 그 연속에 속해야 이 수가 만든 사/오목으로 인정 — 멀리 떨어진 빈칸 채움은 제외)
     */
    private boolean madeExactFiveThrough(int[] line, int e) {
        if (line[CENTER] != SELF) return false;
        int start = CENTER, end = CENTER;
        while (start - 1 >= 0 && line[start - 1] == SELF) start--;
        while (end + 1 < WINDOW && line[end + 1] == SELF) end++;
        return e >= start && e <= end && (end - start + 1) == 5;
    }

    /**
     * 방금 채운 칸 e가 중심 연속 SELF에 포함되고, 그 길이가 정확히 4이며 양 끝이 모두 빈칸이면(열린 사) true.
     * e가 연속에 속하도록 강제해 '이미 사/삼인 형태'가 무관한 빈칸 채움으로 오인되는 것을 막는다.
     */
    private boolean madeOpenFourThrough(int[] line, int e) {
        if (line[CENTER] != SELF) return false;
        int start = CENTER, end = CENTER;
        while (start - 1 >= 0 && line[start - 1] == SELF) start--;
        while (end + 1 < WINDOW && line[end + 1] == SELF) end++;
        if (!(e >= start && e <= end) || (end - start + 1) != 4) return false;
        boolean leftOpen = start - 1 >= 0 && line[start - 1] == EMPTY;
        boolean rightOpen = end + 1 < WINDOW && line[end + 1] == EMPTY;
        return leftOpen && rightOpen;
    }
}
