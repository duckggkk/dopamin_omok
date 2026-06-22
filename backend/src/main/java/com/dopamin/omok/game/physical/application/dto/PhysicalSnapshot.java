package com.dopamin.omok.game.physical.application.dto;

import com.dopamin.omok.game.application.dto.CharacterSkinResponse;
import com.dopamin.omok.game.application.dto.StoneSkinResponse;

import java.util.List;

/**
 * 피지컬 오목 한 프레임의 전체 상태. 서버가 틱마다(그리고 입력 직후) /topic/room/{roomCode}/physical 로 브로드캐스트한다.
 * 클라이언트는 이 스냅샷만 신뢰해 렌더하며(서버 권위), 캐릭터 위치는 스냅샷 사이를 보간해 부드럽게 표시한다.
 */
public record PhysicalSnapshot(
        String roomCode,
        String status,          // IN_PROGRESS / FINISHED
        int boardSize,
        int winCount,
        int[][] cells,          // [y][x]: 0=빈칸,1=흑,2=백,3=분화구
        List<PhysicalPlayerView> players,
        List<ItemDropView> items,
        String winnerColor,     // FINISHED 일 때 승자 색(BLACK/WHITE), 없으면 null
        String pendingWinColor, // 5목 완성 후 확정 대기 중인 색(이 동안 끊으면 무효), 없으면 null
        int[][] pendingWinLine, // 확정 대기 중인 5목을 이루는 칸 좌표들([[x,y],...]) — 강조 표시용, 없으면 null
        long serverTime,        // epoch ms — 클라가 쿨다운/부스트 잔여를 계산하는 기준 시각
        long playStartAt        // epoch ms — 이 시각부터 플레이 시작. serverTime < playStartAt 이면 시작 카운트다운 중
) {

    /** 한 플레이어의 가시 상태(색으로 식별 — 클라는 자기 색과 매칭해 '나'를 찾는다). */
    public record PhysicalPlayerView(
            String color,             // BLACK / WHITE
            String nickname,
            int x,
            int y,
            String heldItem,          // 보유 아이템 종류명(없으면 null)
            long destroyReadyAt,      // 파괴 쿨다운 해제 시각(epoch ms) — HUD 링 표시용
            boolean speedBoosted,
            StoneSkinResponse skin,        // 서버가 해석한 바둑알 스킨 색(미장착 null)
            CharacterSkinResponse character, // 서버가 해석한 캐릭터 스킨(미장착 null)
            String soundAssetKey           // 장착 착수음 assetKey(미장착 null) — 착수 시 양쪽 재생
    ) {}

    /** 필드에 놓인 아이템 드롭. */
    public record ItemDropView(int x, int y, String type) {}
}
