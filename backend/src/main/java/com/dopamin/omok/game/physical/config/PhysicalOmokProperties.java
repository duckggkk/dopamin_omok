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
        int targetScore,         // 오목 완성 1점, 먼저 이 점수에 도달하면 승리(보드 유지·완성 라인만 제거)
        long moveCooldownMs,
        long placeCooldownMs,
        long destroyCooldownMs,
        long winSettleMs,        // 5목 완성 후 승리 확정까지 유지돼야 하는 시간(오프닝 직선 러시 방지 — 그 사이 끊으면 무효)
        long countdownMs,        // 게임 시작 시 보드/캐릭터 세팅 후 플레이 개시까지의 카운트다운(3·2·1)
        long tickIntervalMs,
        long itemSpawnIntervalMs,
        int maxItemsOnField,
        long speedBoostDurationMs,
        long speedBoostMoveCooldownMs,
        Map<PhysicalItemType, Integer> itemWeights,
        boolean trainingLogEnabled,  // true 면 게임마다 ML 학습용 행동 로그를 기록(서버 전용 저장)
        long inputBufferMs,          // 쿨다운에 막힌 착수 입력을 이 시간 안이면 버퍼했다가 풀리는 즉시 1회 실행(착수 씹힘 방지)
        int moveQueueMax             // 이동 입력 버퍼 큐 최대 길이 — 방향을 빠르게 번갈아 눌러도 순서대로 소화(클수록 더 많이 버퍼, 멈춘 뒤 잔여 이동도 길어짐)
) {
    public PhysicalOmokProperties {
        // 설정 누락 시(예: 테스트 프로파일) 안전한 기본값으로 보정 — 게임이 0ms 쿨다운/0 크기로 깨지지 않게 함.
        if (boardSize <= 0) boardSize = 14;
        if (winCount <= 0) winCount = 5;
        if (targetScore <= 0) targetScore = 3;
        if (moveCooldownMs <= 0) moveCooldownMs = 140;
        if (placeCooldownMs <= 0) placeCooldownMs = 150;
        if (destroyCooldownMs < 0) destroyCooldownMs = 0;   // 0 = 파괴 쿨다운 없음(음수만 보정)
        if (winSettleMs <= 0) winSettleMs = 2000;
        if (countdownMs < 0) countdownMs = 0;   // 0 = 카운트다운 없이 즉시 시작(음수만 보정)
        if (tickIntervalMs <= 0) tickIntervalMs = 60;
        if (itemSpawnIntervalMs <= 0) itemSpawnIntervalMs = 7000;
        if (maxItemsOnField <= 0) maxItemsOnField = 3;
        if (speedBoostDurationMs <= 0) speedBoostDurationMs = 5000;
        if (speedBoostMoveCooldownMs <= 0) speedBoostMoveCooldownMs = 55;
        if (inputBufferMs < 0) inputBufferMs = 170;   // 0 = 버퍼링 끔(음수만 보정)
        if (moveQueueMax <= 0) moveQueueMax = 6;      // 미설정 시 기본 6(위·오른쪽 번갈아 빠르게 눌러도 순서대로 소화)
        if (itemWeights == null || itemWeights.isEmpty()) {
            itemWeights = new EnumMap<>(PhysicalItemType.class);
            itemWeights.put(PhysicalItemType.SPEED_BOOST, 50);
            itemWeights.put(PhysicalItemType.CRATER, 30);
            itemWeights.put(PhysicalItemType.BOMB, 20);
        }
    }
}
