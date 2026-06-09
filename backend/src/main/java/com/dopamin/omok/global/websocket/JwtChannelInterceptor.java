package com.dopamin.omok.global.websocket;

import com.dopamin.omok.global.security.jwt.JwtAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
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
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                String token = authHeader.substring(BEARER_PREFIX.length());
                // HTTP 필터와 동일하게 서명 + tokenVersion 검증을 통과한 경우에만 인증 부여.
                // (로그아웃/재로그인으로 무효화된 토큰으로는 WebSocket 연결 불가)
                jwtAuthenticator.authenticate(token).ifPresentOrElse(
                        accessor::setUser,
                        () -> log.warn("WebSocket JWT authentication failed for CONNECT")
                );
            }
        }
        return message;
    }
}
