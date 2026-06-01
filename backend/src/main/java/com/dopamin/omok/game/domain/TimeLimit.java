package com.dopamin.omok.game.domain;

public enum TimeLimit {
    UNLIMITED(null),
    ONE_MIN(60),
    THREE_MIN(180),
    FIVE_MIN(300),
    TEN_MIN(600);

    private final Integer seconds;

    TimeLimit(Integer seconds) {
        this.seconds = seconds;
    }

    public Integer getSeconds() {
        return seconds;
    }

    public boolean isUnlimited() {
        return seconds == null;
    }
}
