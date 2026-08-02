package com.dopamin.omok.plaza.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 광장 런타임 설정. application.yml 의 app.plaza 에서 주입된다(없으면 안전한 기본값).
 * 월드 크기·채널 정원·틱·이동 속도를 코드 수정 없이 조절(확장성 우선).
 */
@ConfigurationProperties(prefix = "app.plaza")
public record PlazaProperties(
        int worldWidth,
        int worldHeight,
        int channelCapacity,
        long tickIntervalMs,
        int moveStep,       // 틱당 이동 픽셀
        int spawnMargin,    // 스폰 시 월드 경계로부터의 최소 여백
        long joinTimeoutMs  // REST allocate 후 이 시간 내 WS join 이 안 오면 좀비 채널로 간주해 정리
) {
    public PlazaProperties {
        if (worldWidth <= 0) worldWidth = 1600;
        if (worldHeight <= 0) worldHeight = 1200;
        if (channelCapacity <= 0) channelCapacity = 30;
        if (tickIntervalMs <= 0) tickIntervalMs = 100; // 걷기엔 100ms면 충분(클라가 보간)
        if (moveStep <= 0) moveStep = 12;
        if (spawnMargin < 0) spawnMargin = 100;
        if (joinTimeoutMs <= 0) joinTimeoutMs = 30_000;
    }
}
