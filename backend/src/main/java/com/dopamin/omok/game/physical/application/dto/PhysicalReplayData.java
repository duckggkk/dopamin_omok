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
 * @param motionFrames 캐릭터·아이템 위치 시계열(영상 리플레이용). 일정 간격으로 샘플링한 위치 스냅샷.
 *                     보드 상태는 events 로 재구성하고, 이 트랙으로 캐릭터의 '움직임'을 부드럽게 재생한다.
 *                     구버전 리플레이엔 없어 null — 클라가 단계별 뷰어로 폴백.
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
        List<MotionFrame> motionFrames,
        PhysicalTrainingLog trainingLog
) {
    public record PlayerInfo(String color, String nickname) {}

    /** t: 게임 시작 기준 경과 ms, (x,y): 칸 좌표, v: 새 칸 값. */
    public record CellChange(long t, int x, int y, int v) {}

    /** 한 시점의 위치 스냅샷 — 캐릭터들과 필드 아이템의 좌표. */
    public record MotionFrame(long t, List<PlayerMotion> players, List<ItemMotion> items) {}

    /** 한 캐릭터의 위치/상태(영상 보간용). heldItem/speedBoosted 는 HUD·부스트 링 표시에 쓸 수 있다. */
    public record PlayerMotion(String color, int x, int y, String heldItem, boolean speedBoosted) {}

    /** 필드에 떨어진 아이템 위치. */
    public record ItemMotion(int x, int y, String type) {}

    /** 클라이언트 전송용 사본 — 서버 전용 학습 로그만 제거한다(영상 트랙은 유지). */
    public PhysicalReplayData forClient() {
        return trainingLog == null
                ? this
                : new PhysicalReplayData(gameId, boardSize, winCount, durationMs, winnerColor,
                        players, events, motionFrames, null);
    }
}
