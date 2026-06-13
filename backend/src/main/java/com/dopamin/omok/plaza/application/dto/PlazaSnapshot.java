package com.dopamin.omok.plaza.application.dto;

import com.dopamin.omok.plaza.domain.PlazaAppearance;

import java.util.List;

/**
 * 광장 한 채널의 한 프레임 상태. 서버가 틱마다 /topic/plaza/{channelId} 로 raw 브로드캐스트(ApiResponse 미래핑).
 * 클라는 이 스냅샷만 신뢰해 렌더하며(서버 권위), 좌표는 스냅샷 사이를 보간해 부드럽게 표시한다.
 */
public record PlazaSnapshot(
        String channelId,
        int worldWidth,
        int worldHeight,
        List<PlazaPlayerView> players,
        long serverTime
) {
    /** 한 플레이어의 가시 상태. 클라는 playerId(자기 publicId)로 '나'를 찾는다. */
    public record PlazaPlayerView(
            String playerId,   // publicId(UUID) — 외부 식별
            String nickname,
            int x,
            int y,
            String facing,     // UP/DOWN/LEFT/RIGHT
            boolean moving,
            PlazaAppearance appearance
    ) {}
}
