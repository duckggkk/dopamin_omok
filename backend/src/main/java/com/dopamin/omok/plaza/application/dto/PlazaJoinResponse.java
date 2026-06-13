package com.dopamin.omok.plaza.application.dto;

/** 채널 배정 결과(REST). 클라는 이 channelId 토픽을 구독한 뒤 WS join 으로 실제 입장한다. */
public record PlazaJoinResponse(
        String channelId,
        int worldWidth,
        int worldHeight,
        int capacity
) {
}
