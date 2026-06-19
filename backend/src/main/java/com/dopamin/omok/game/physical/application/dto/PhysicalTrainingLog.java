package com.dopamin.omok.game.physical.application.dto;

import java.util.List;

/**
 * 피지컬 오목 한 판의 ML 학습용 로그(서버 전용 — 클라이언트로 내려가지 않는다).
 *
 * 사용자용 리플레이({@link PhysicalReplayData})가 '보드 결과'만 담는 것과 달리, 이건 '누가 언제 어떤 행동을
 * 했는가'(state→action)를 담아 모방학습/분석에 쓴다. 시뮬레이션이 서버 권위·결정론적이라,
 * 초기 위치 + 행동 스트림 + (랜덤인) 아이템 스폰만 있으면 임의 시점의 전체 상태를 오프라인에서 재구성할 수 있다.
 *
 * 메모리 버퍼에 모았다가 게임 종료 시 1회만 직렬화되므로(틱마다 DB 쓰기 없음) 런타임 부하는 무시할 만하다.
 *
 * @param players    초기 상태(색·닉네임·시작 좌표·봇 여부)
 * @param actions    양 플레이어의 모든 행동(사람 입력 + 봇 결정) — (상태→행동) 라벨
 * @param itemSpawns 아이템 스폰(랜덤 이벤트) — 결정론적 재구성을 위해 필요
 */
public record PhysicalTrainingLog(
        List<StartState> players,
        List<Action> actions,
        List<ItemSpawn> itemSpawns
) {
    public record StartState(String color, String nickname, int x, int y, boolean bot) {}

    /**
     * @param t    게임 시작 기준 경과 ms
     * @param type MOVE_START | MOVE_STOP | PLACE | DESTROY | USE_ITEM
     * @param dir  MOVE_START 일 때 방향(UP/DOWN/LEFT/RIGHT), 그 외 null
     * @param x,y  행동 직전 행위자 위치(상태 앵커)
     */
    public record Action(long t, String color, String type, String dir, int x, int y) {}

    public record ItemSpawn(long t, int x, int y, String type) {}
}
