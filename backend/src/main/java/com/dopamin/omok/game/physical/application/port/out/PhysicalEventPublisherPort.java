package com.dopamin.omok.game.physical.application.port.out;

import com.dopamin.omok.game.physical.application.dto.PhysicalSnapshot;

/**
 * 피지컬 오목 실시간 이벤트 발행 아웃 포트(infra 격리).
 * PhysicalGameSessionManager 는 이 포트로만 스냅샷을 브로드캐스트하고,
 * 실제 전송 수단(STOMP SimpleBroker → 추후 외부 브로커)은 어댑터가 결정한다.
 */
public interface PhysicalEventPublisherPort {

    /** 진행 중인 세션의 전체 스냅샷을 방 구독자 전원에게 브로드캐스트. */
    void publishSnapshot(String roomCode, PhysicalSnapshot snapshot);
}
