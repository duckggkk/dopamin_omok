package com.dopamin.omok.game.physical.adapter.in.web;

import com.dopamin.omok.game.physical.adapter.in.web.dto.PhysicalInputRequest;
import com.dopamin.omok.game.physical.application.PhysicalGameSessionManager;
import com.dopamin.omok.global.security.principal.AuthUser;
import com.dopamin.omok.global.websocket.WebSocketSessionRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

/**
 * 피지컬 오목 실시간 입력 수신. 스냅샷 브로드캐스트는 SessionManager 가 담당하므로 여기서는 @SendTo 가 없다.
 * 인증된 userId 만 전달하며, 참가자 여부 검증/쿨다운/범위는 전부 SessionManager·엔진(서버 권위)에서 강제한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PhysicalGameWebSocketController {

    private final PhysicalGameSessionManager manager;
    private final WebSocketSessionRegistry sessionRegistry;

    @MessageMapping("/physical/{roomCode}/input")
    public void handleInput(
            @DestinationVariable String roomCode,
            @Valid @Payload PhysicalInputRequest request,
            SimpMessageHeaderAccessor accessor) {
        Long userId = extractUserId(accessor);
        if (userId == null) return;
        registerSession(accessor, roomCode, userId);
        manager.applyInput(roomCode, userId, request.type(), request.direction());
    }

    @MessageMapping("/physical/{roomCode}/surrender")
    public void handleSurrender(
            @DestinationVariable String roomCode,
            SimpMessageHeaderAccessor accessor) {
        Long userId = extractUserId(accessor);
        if (userId == null) return;
        registerSession(accessor, roomCode, userId);
        manager.surrender(roomCode, userId);
    }

    private Long extractUserId(SimpMessageHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken authToken
                && authToken.getPrincipal() instanceof AuthUser user) {
            return user.id();
        }
        return null;
    }

    private void registerSession(SimpMessageHeaderAccessor accessor, String roomCode, Long userId) {
        if (accessor.getSessionId() != null) {
            sessionRegistry.register(accessor.getSessionId(), roomCode, userId);
        }
    }
}
