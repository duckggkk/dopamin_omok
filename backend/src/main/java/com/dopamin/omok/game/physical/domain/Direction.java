package com.dopamin.omok.game.physical.domain;

/**
 * 8방향 이동(상하좌우 + 대각). 좌표계: x=열, y=행, 원점 좌상단.
 * UP 은 y 감소, DOWN 은 y 증가.
 * 대각은 두 축이 동시에 1이라 그대로 두면 √2 배 빨라진다 — 속도 보정은 엔진(advanceContinuous)이 한다.
 */
public enum Direction {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    UP_LEFT(-1, -1),
    UP_RIGHT(1, -1),
    DOWN_LEFT(-1, 1),
    DOWN_RIGHT(1, 1);

    /** 상하좌우만 — 격자 탐색(봇 BFS)처럼 대각을 쓰면 안 되는 곳에서 values() 대신 쓴다. */
    public static final Direction[] CARDINALS = {UP, DOWN, LEFT, RIGHT};

    public final int dx;
    public final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public boolean isDiagonal() {
        return dx != 0 && dy != 0;
    }
}
