package com.dopamin.omok.game.physical.application.dto;

import java.util.List;

/**
 * 피지컬 오목 리플레이 데이터(영속/전송 공용).
 * 보드 칸 변화 이벤트만 시간순으로 담아, 클라가 임의 시점의 보드를 재구성한다.
 *
 * @param events 칸 변화 스트림. v: 0=빈칸, 1=흑, 2=백, 3=분화구 (PhysicalBoard.encode 와 동일)
 */
public record PhysicalReplayData(
        Long gameId,
        int boardSize,
        int winCount,
        long durationMs,
        String winnerColor,        // "BLACK" | "WHITE" | null(무효/중단)
        List<PlayerInfo> players,
        List<CellChange> events
) {
    public record PlayerInfo(String color, String nickname) {}

    /** t: 게임 시작 기준 경과 ms, (x,y): 칸 좌표, v: 새 칸 값. */
    public record CellChange(long t, int x, int y, int v) {}
}
