package com.dopamin.omok.plaza.adapter.out.websocket;

import com.dopamin.omok.game.application.dto.ChatMessageResponse;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.plaza.application.dto.PlazaSnapshot;
import com.dopamin.omok.plaza.application.port.out.PlazaEventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * STOMP(SimpMessagingTemplate) 기반 광장 이벤트 발행 어댑터 — {@link PlazaEventPublisherPort} 구현.
 * 멀티 서버로 확장할 때 SimpleBroker 를 외부 브로커로 바꾸면 이 어댑터만 교체하면 된다.
 */
@Component
@RequiredArgsConstructor
public class PlazaWebSocketPublisherAdapter implements PlazaEventPublisherPort {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publishSnapshot(String channelId, PlazaSnapshot snapshot) {
        messagingTemplate.convertAndSend("/topic/plaza/" + channelId, snapshot);
    }

    @Override
    public void publishChat(String channelId, ChatMessageResponse message) {
        messagingTemplate.convertAndSend("/topic/plaza/" + channelId + "/chat", ApiResponse.success(message));
    }
}
