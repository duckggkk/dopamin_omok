package com.dopamin.omok.global.websocket;

import com.dopamin.omok.global.security.jwt.JwtAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtAuthenticator jwtAuthenticator;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            String token = (authHeader != null && authHeader.startsWith(BEARER_PREFIX))
                    ? authHeader.substring(BEARER_PREFIX.length())
                    : null;

            // CONNECT 단계에서 유효한 JWT(서명 + tokenVersion 일치)가 없으면 연결 자체를 거부한다.
            // 미인증 클라이언트가 임의의 방 토픽(/topic/room/{code})을 구독해
            // 타인의 수순·채팅을 열람하는 것을 차단한다(로그아웃 토큰도 거부).
            UsernamePasswordAuthenticationToken authentication = jwtAuthenticator.authenticate(token)
                    .orElseThrow(() -> {
                        log.warn("WebSocket CONNECT 거부 — 유효한 인증 토큰 없음");
                        return new MessagingException("WebSocket 인증에 실패했습니다.");
                    });
            accessor.setUser(authentication);
        }
        return message;
    }
}
