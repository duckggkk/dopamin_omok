package com.dopamin.omok.plaza.domain;

import lombok.Getter;

/** 광장 한 명의 런타임 상태(메모리 전용, 비영속). */
@Getter
public class PlazaPlayer {

    private final Long userId;     // 내부 식별(서버 전용)
    private final String publicId; // 외부 노출 식별(UUID 문자열)
    private final String nickname;
    private int x;
    private int y;
    private Direction facing = Direction.DOWN;
    private boolean moving = false;
    private PlazaAppearance appearance;

    public PlazaPlayer(Long userId, String publicId, String nickname, int x, int y, PlazaAppearance appearance) {
        this.userId = userId;
        this.publicId = publicId;
        this.nickname = nickname;
        this.x = x;
        this.y = y;
        this.appearance = appearance != null ? appearance : PlazaAppearance.DEFAULT;
    }

    public void startMove(Direction direction) {
        if (direction != null) this.facing = direction;
        this.moving = true;
    }

    public void stopMove() {
        this.moving = false;
    }

    public void setAppearance(PlazaAppearance appearance) {
        if (appearance != null) this.appearance = appearance;
    }

    /** facing 방향으로 step 만큼 이동 후 월드 경계로 clamp. */
    public void step(int step, int worldWidth, int worldHeight) {
        this.x = clamp(this.x + facing.dx() * step, 0, worldWidth);
        this.y = clamp(this.y + facing.dy() * step, 0, worldHeight);
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }
}
