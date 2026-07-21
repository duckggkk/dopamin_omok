package com.dopamin.omok.game.physical.application.dto;

import com.dopamin.omok.game.application.dto.CharacterSkinResponse;
import com.dopamin.omok.game.application.dto.StoneSkinResponse;
import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.physical.domain.PhysicalItemType;

import java.util.List;

/**
 * 피지컬 오목 한 프레임의 전체 상태. 서버가 틱마다(그리고 입력 직후) /topic/room/{roomCode}/physical 로 브로드캐스트한다.
 * 클라이언트는 이 스냅샷만 신뢰해 렌더하며(서버 권위), 캐릭터 위치는 스냅샷 사이를 보간해 부드럽게 표시한다.
 *
 * 내보내기 전용(브로드캐스트)이라 서버가 되읽는 일이 없다 — enum 은 JSON 으로 상수명 문자열이 되어 나간다.
 */
public record PhysicalSnapshot(
        String roomCode,
        GameStatus status,      // IN_PROGRESS / FINISHED
        int boardSize,
        int winCount,
        int[][] cells,          // [y][x]: 0=빈칸,1=흑,2=백,3=분화구
        List<PhysicalPlayerView> players,
        List<ItemDropView> items,
        StoneColor winnerColor, // FINISHED 일 때 승자 색, 없으면 null
        List<PendingLineView> pendingLines, // 충전 중인 완성 라인들(각자 게이지) — 빈 리스트면 없음
        long settleMs,          // 게이지 총 충전 시간(ms) — 클라가 lockAt 과 함께 충전 비율을 계산
        long serverTime,        // epoch ms — 클라가 쿨다운/부스트/게이지 잔여를 계산하는 기준 시각
        long playStartAt,       // epoch ms — 이 시각부터 플레이 시작. serverTime < playStartAt 이면 시작 카운트다운 중
        int targetScore,        // 먼저 이 점수에 도달하면 승리(오목 1개 = 1점)
        int scoreEventId,       // 득점(라인 파괴) 1회성 신호 — 클라가 증가를 감지해 특수효과 재생
        List<ClearedLineView> lastClearedLines // 직전 틱에 파괴+득점된 라인들 — 줄별 파괴 연출용(빈 리스트면 없음)
) {

    /** 충전 중인 완성 라인 한 줄(게이지). lockAt 까지 채워지면 파괴+득점. */
    public record PendingLineView(StoneColor color, int[][] cells, long lockAt) {}

    /** 직전 틱에 파괴+득점된 라인 한 줄(파괴 연출용). */
    public record ClearedLineView(StoneColor color, int[][] cells) {}

    /** 한 플레이어의 가시 상태(색으로 식별 — 클라는 자기 색과 매칭해 '나'를 찾는다). */
    public record PhysicalPlayerView(
            StoneColor color,
            String nickname,
            double x,                 // 칸 단위 위치(격자 모드는 정수값, 연속 모드는 소수값) — 클라가 보간해 렌더
            double y,
            PhysicalItemType heldItem, // 보유 아이템 종류(없으면 null)
            long destroyReadyAt,      // 파괴 쿨다운 해제 시각(epoch ms) — HUD 링 표시용
            boolean speedBoosted,
            StoneSkinResponse skin,        // 서버가 해석한 바둑알 스킨 색(미장착 null)
            CharacterSkinResponse character, // 서버가 해석한 캐릭터 스킨(미장착 null)
            String soundAssetKey,          // 장착 착수음 assetKey(미장착 null) — 착수 시 양쪽 재생
            int score                      // 이 플레이어의 누적 점수(완성한 오목 개수)
    ) {}

    /** 필드에 놓인 아이템 드롭. */
    public record ItemDropView(int x, int y, PhysicalItemType type) {}
}
