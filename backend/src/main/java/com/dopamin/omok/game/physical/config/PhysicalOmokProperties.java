package com.dopamin.omok.game.physical.config;

import com.dopamin.omok.game.physical.domain.PhysicalItemType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * 피지컬 오목 런타임/밸런스 설정. application.yml 의 app.physical-omok 에서 주입된다.
 * 보드 크기·쿨다운·아이템 스폰/가중치 등 모든 밸런스 값을 코드 수정 없이 여기서 조절한다(확장성·변경성 최우선).
 */
@ConfigurationProperties(prefix = "app.physical-omok")
public record PhysicalOmokProperties(
        int boardSize,
        int winCount,
        long moveCooldownMs,
        long placeCooldownMs,
        long destroyCooldownMs,
        long winSettleMs,        // 5목 완성 후 승리 확정까지 유지돼야 하는 시간(오프닝 직선 러시 방지 — 그 사이 끊으면 무효)
        long tickIntervalMs,
        long itemSpawnIntervalMs,
        int maxItemsOnField,
        long speedBoostDurationMs,
        long speedBoostMoveCooldownMs,
        Map<PhysicalItemType, Integer> itemWeights
) {
    public PhysicalOmokProperties {
        // 설정 누락 시(예: 테스트 프로파일) 안전한 기본값으로 보정 — 게임이 0ms 쿨다운/0 크기로 깨지지 않게 함.
        if (boardSize <= 0) boardSize = 14;
        if (winCount <= 0) winCount = 5;
        if (moveCooldownMs <= 0) moveCooldownMs = 140;
        if (placeCooldownMs <= 0) placeCooldownMs = 150;
        if (destroyCooldownMs <= 0) destroyCooldownMs = 2000;
        if (winSettleMs <= 0) winSettleMs = 2000;
        if (tickIntervalMs <= 0) tickIntervalMs = 60;
        if (itemSpawnIntervalMs <= 0) itemSpawnIntervalMs = 7000;
        if (maxItemsOnField <= 0) maxItemsOnField = 3;
        if (speedBoostDurationMs <= 0) speedBoostDurationMs = 5000;
        if (speedBoostMoveCooldownMs <= 0) speedBoostMoveCooldownMs = 55;
        if (itemWeights == null || itemWeights.isEmpty()) {
            itemWeights = new EnumMap<>(PhysicalItemType.class);
            itemWeights.put(PhysicalItemType.SPEED_BOOST, 50);
            itemWeights.put(PhysicalItemType.CRATER, 30);
            itemWeights.put(PhysicalItemType.BOMB, 20);
        }
    }
}
