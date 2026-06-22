package com.dopamin.omok.friend.application.dto;

/** 두 사용자 간 상대 전적(나 기준 승/패/무). */
public record HeadToHead(int wins, int losses, int draws) {
    public static HeadToHead empty() {
        return new HeadToHead(0, 0, 0);
    }

    public int total() {
        return wins + losses + draws;
    }
}
