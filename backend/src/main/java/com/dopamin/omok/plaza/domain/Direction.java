package com.dopamin.omok.plaza.domain;

/** 광장 이동/바라보는 방향. 스프라이트 시트의 행(row)과 1:1 대응(클라 렌더). */
public enum Direction {
    UP, DOWN, LEFT, RIGHT;

    public int dx() {
        return this == LEFT ? -1 : this == RIGHT ? 1 : 0;
    }

    public int dy() {
        return this == UP ? -1 : this == DOWN ? 1 : 0;
    }
}
