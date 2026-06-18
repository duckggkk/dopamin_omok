package com.dopamin.omok.game.physical.adapter.out.websocket;

import com.dopamin.omok.game.physical.application.dto.PhysicalSnapshot;
import com.dopamin.omok.game.physical.application.port.out.PhysicalEventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * STOMP(SimpMessagingTemplate) 기반 피지컬 오목 이벤트 발행 어댑터
 * — {@link PhysicalEventPublisherPort} 구현.
 * 멀티 서버로 확장할 때 SimpleBroker 를 외부 브로커로 바꾸면 이 어댑터만 교체하면 된다.
 */
@Component
@RequiredArgsConstructor
public class PhysicalWebSocketPublisherAdapter implements PhysicalEventPublisherPort {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publishSnapshot(String roomCode, PhysicalSnapshot snapshot) {
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/physical", snapshot);
    }
}
