package com.dopamin.omok.game.physical.domain;

import com.dopamin.omok.game.domain.StoneColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PhysicalBoardTest {

    private static final int SIZE = 14;
    private static final int WIN = 5;

    @Test
    @DisplayName("가로 5연속이면 승리(보드 14, 승리수 5)")
    void horizontalWin() {
        PhysicalBoard board = new PhysicalBoard(SIZE);
        for (int x = 0; x < 5; x++) board.place(x, 7, StoneColor.BLACK);
        assertThat(board.checkWin(4, 7, WIN)).isTrue();
    }

    @Test
    @DisplayName("세로 5연속이면 승리")
    void verticalWin() {
        PhysicalBoard board = new PhysicalBoard(SIZE);
        for (int y = 0; y < 5; y++) board.place(7, y, StoneColor.WHITE);
        assertThat(board.checkWin(7, 4, WIN)).isTrue();
    }

    @Test
    @DisplayName("대각선(\\)·역대각선(/) 5연속이면 승리")
    void diagonalWin() {
        PhysicalBoard a = new PhysicalBoard(SIZE);
        for (int i = 0; i < 5; i++) a.place(i, i, StoneColor.BLACK);
        assertThat(a.checkWin(2, 2, WIN)).isTrue();

        PhysicalBoard b = new PhysicalBoard(SIZE);
        for (int i = 0; i < 5; i++) b.place(i, 4 - i, StoneColor.WHITE);
        assertThat(b.checkWin(2, 2, WIN)).isTrue();
    }

    @Test
    @DisplayName("4연속은 승리가 아니다")
    void fourIsNotWin() {
        PhysicalBoard board = new PhysicalBoard(SIZE);
        for (int x = 0; x < 4; x++) board.place(x, 7, StoneColor.BLACK);
        assertThat(board.checkWin(3, 7, WIN)).isFalse();
    }

    @Test
    @DisplayName("분화구(BLOCKED)는 라인을 끊고 영구 착수 불가다")
    void craterBreaksLineAndBlocks() {
        PhysicalBoard board = new PhysicalBoard(SIZE);
        for (int x = 0; x < 5; x++) board.place(x, 7, StoneColor.BLACK);
        board.crater(2, 7); // 가운데 돌 파괴 + 영구 블록

        assertThat(board.stoneAt(2, 7)).isNull();
        assertThat(board.isBlocked(2, 7)).isTrue();
        assertThat(board.isPlaceable(2, 7)).isFalse();
        assertThat(board.checkWin(4, 7, WIN)).isFalse(); // 라인 끊김
    }

    @Test
    @DisplayName("폭탄은 3x3 내 모든 돌(흑+백)을 제거하고 분화구는 남긴다")
    void bombRemovesAllStonesInArea() {
        PhysicalBoard board = new PhysicalBoard(SIZE);
        board.place(4, 4, StoneColor.BLACK);
        board.place(5, 5, StoneColor.BLACK);
        board.place(6, 6, StoneColor.WHITE);
        board.place(4, 5, StoneColor.WHITE);
        board.place(6, 4, StoneColor.BLACK);
        board.crater(5, 4); // 분화구는 유지돼야 함

        board.bombArea(5, 5);

        for (int y = 4; y <= 6; y++)
            for (int x = 4; x <= 6; x++)
                assertThat(board.stoneAt(x, y)).as("(%d,%d)", x, y).isNull();
        assertThat(board.isBlocked(5, 4)).isTrue();
    }

    @Test
    @DisplayName("findWinningLine: 5목을 이루는 칸 좌표를 끝에서 끝까지 반환(강조 표시용)")
    void findWinningLineReturnsCells() {
        PhysicalBoard board = new PhysicalBoard(SIZE);
        for (int x = 3; x < 8; x++) board.place(x, 6, StoneColor.BLACK); // (3,6)~(7,6)

        List<int[]> line = board.findWinningLine(StoneColor.BLACK, WIN);

        assertThat(line).hasSize(5);
        assertThat(line.get(0)).containsExactly(3, 6);
        assertThat(line.get(4)).containsExactly(7, 6);
        // 백은 라인이 없으니 빈 리스트
        assertThat(board.findWinningLine(StoneColor.WHITE, WIN)).isEmpty();
    }

    @Test
    @DisplayName("findWinningLine: 6목이면 양 끝까지 6칸 모두 포함")
    void findWinningLineIncludesOverline() {
        PhysicalBoard board = new PhysicalBoard(SIZE);
        for (int y = 2; y < 8; y++) board.place(5, y, StoneColor.WHITE); // 세로 6목

        List<int[]> line = board.findWinningLine(StoneColor.WHITE, WIN);

        assertThat(line).hasSize(6);
        assertThat(line.get(0)).containsExactly(5, 2);
        assertThat(line.get(5)).containsExactly(5, 7);
    }

    @Test
    @DisplayName("착수 가능: 빈칸만. 돌/분화구/범위 밖은 불가")
    void placeableRules() {
        PhysicalBoard board = new PhysicalBoard(SIZE);
        assertThat(board.isPlaceable(0, 0)).isTrue();

        board.place(0, 0, StoneColor.BLACK);
        assertThat(board.isPlaceable(0, 0)).isFalse();

        board.removeStone(0, 0);
        assertThat(board.isPlaceable(0, 0)).isTrue();

        assertThat(board.isPlaceable(-1, 0)).isFalse();
        assertThat(board.isPlaceable(SIZE, 0)).isFalse();
    }
}
