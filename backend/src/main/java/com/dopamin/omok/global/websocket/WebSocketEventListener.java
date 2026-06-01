package com.dopamin.omok.global.websocket;

import com.dopamin.omok.game.application.service.RoomService;
import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final WebSocketSessionRegistry sessionRegistry;
    private final RoomService roomService;
    private final DisconnectGraceManager disconnectGraceManager;

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();

        if (destination == null || sessionId == null) return;

        if (destination.matches("/topic/room/[^/]+/status")) {
            String roomCode = destination.split("/")[3];
            Long userId = extractUserId(accessor);
            if (userId != null) {
                sessionRegistry.register(sessionId, roomCode, userId);
                // 재접속 시 유예 타이머 취소
                if (disconnectGraceManager.cancelGrace(roomCode, userId)) {
                    log.debug("Reconnected: grace cancelled room={} user={}", roomCode, userId);
                }
                log.debug("WebSocket subscribed: session={} room={} user={}", sessionId, roomCode, userId);
            }
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        sessionRegistry.getSession(sessionId).ifPresent(info -> {
            log.debug("WebSocket disconnected: session={} room={} user={}", sessionId, info.roomCode(), info.userId());
            sessionRegistry.remove(sessionId);
            roomService.handleDisconnect(info.roomCode(), info.userId());
        });
    }

    private Long extractUserId(StompHeaderAccessor accessor) {
        try {
            if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken authToken
                    && authToken.getPrincipal() instanceof CustomUserDetails userDetails) {
                return userDetails.getId();
            }
        } catch (Exception e) {
            log.debug("Could not extract userId from WebSocket session: {}", e.getMessage());
        }
        return null;
    }
}
