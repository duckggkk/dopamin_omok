package com.dopamin.omok.game.physical.domain;

/**
 * 4방향 이동(크레이지 아케이드식, 대각 없음). 좌표계: x=열, y=행, 원점 좌상단.
 * UP 은 y 감소, DOWN 은 y 증가.
 */
public enum Direction {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    public final int dx;
    public final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }
}
