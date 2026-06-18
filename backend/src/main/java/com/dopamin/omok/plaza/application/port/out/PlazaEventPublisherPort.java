package com.dopamin.omok.plaza.application.port.out;

import com.dopamin.omok.game.application.dto.ChatMessageResponse;
import com.dopamin.omok.plaza.application.dto.PlazaSnapshot;

/**
 * 광장 실시간 이벤트 발행 아웃 포트(infra 격리).
 * application 계층(PlazaSessionManager·PlazaWebSocketController)은 이 포트로만 브로드캐스트하고,
 * 실제 전송 수단(STOMP SimpleBroker → 추후 Redis/RabbitMQ 릴레이)은 어댑터가 결정한다.
 */
public interface PlazaEventPublisherPort {

    /** 채널 스냅샷(좌표/외형)을 해당 채널 구독자 전원에게 브로드캐스트. */
    void publishSnapshot(String channelId, PlazaSnapshot snapshot);

    /** 광장 채팅 메시지를 해당 채널 구독자 전원에게 브로드캐스트. */
    void publishChat(String channelId, ChatMessageResponse message);
}
