package com.dopamin.omok.game.physical.application.dto;

import java.util.List;

/**
 * 피지컬 오목 리플레이 데이터(영속/전송 공용).
 * 보드 칸 변화 이벤트만 시간순으로 담아, 클라가 임의 시점의 보드를 재구성한다.
 *
 * trainingLog 는 ML 학습용(서버 전용) 부가 데이터로, 같은 JSON 컬럼에 함께 저장하되
 * 클라이언트로 내보낼 땐 {@link #forClient()} 로 떼어낸다(응답 비대화·데이터 노출 방지).
 * (학습 데이터가 커지면 전용 컬럼/테이블로 분리하는 게 다음 단계.)
 *
 * @param events 칸 변화 스트림. v: 0=빈칸, 1=흑, 2=백, 3=분화구 (PhysicalBoard.encode 와 동일)
 * @param trainingLog (선택) 행동 스트림 학습 로그. 비활성/구버전이면 null
 */
public record PhysicalReplayData(
        Long gameId,
        int boardSize,
        int winCount,
        long durationMs,
        String winnerColor,        // "BLACK" | "WHITE" | null(무효/중단)
        List<PlayerInfo> players,
        List<CellChange> events,
        PhysicalTrainingLog trainingLog
) {
    public record PlayerInfo(String color, String nickname) {}

    /** t: 게임 시작 기준 경과 ms, (x,y): 칸 좌표, v: 새 칸 값. */
    public record CellChange(long t, int x, int y, int v) {}

    /** 클라이언트 전송용 사본 — 서버 전용 학습 로그를 제거한다. */
    public PhysicalReplayData forClient() {
        return trainingLog == null
                ? this
                : new PhysicalReplayData(gameId, boardSize, winCount, durationMs, winnerColor, players, events, null);
    }
}
