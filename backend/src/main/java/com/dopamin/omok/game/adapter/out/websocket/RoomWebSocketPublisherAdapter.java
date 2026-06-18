package com.dopamin.omok.game.adapter.out.websocket;

import com.dopamin.omok.game.application.dto.RoomResponse;
import com.dopamin.omok.game.application.port.out.RoomEventPublisherPort;
import com.dopamin.omok.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class RoomWebSocketPublisherAdapter implements RoomEventPublisherPort {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publishStatus(String roomCode, RoomResponse response) {
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/status", ApiResponse.success(response));
    }

    @Override
    public void publishClosed(String roomCode, String message) {
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/closed", Map.of("message", message));
    }

    @Override
    public void publishNotice(String roomCode, String message) {
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/notice", Map.of("message", message));
    }
}
