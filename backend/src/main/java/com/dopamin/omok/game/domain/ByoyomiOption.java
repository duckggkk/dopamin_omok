package com.dopamin.omok.game.domain;

public enum ByoyomiOption {
    NONE(null),
    TEN_SEC(10),
    FIFTEEN_SEC(15),
    THIRTY_SEC(30);

    private final Integer seconds;

    ByoyomiOption(Integer seconds) {
        this.seconds = seconds;
    }

    public Integer getSeconds() {
        return seconds;
    }

    public boolean hasByoyomi() {
        return seconds != null;
    }
}
