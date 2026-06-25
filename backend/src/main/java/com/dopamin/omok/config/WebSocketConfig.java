package com.dopamin.omok.config;

import com.dopamin.omok.global.websocket.JwtChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final CorsProperties corsProperties;
    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        // 클라이언트가 구독할 브로커 destination prefix 등록
        // /topic은 주로 브로드캐스트, /queue는 주로 1:1 메시지에 사용

        registry.setApplicationDestinationPrefixes("/app");
        // 클라이언트가 서버 @MessageMapping으로 메시지를 보낼 때 사용하는 prefix
        // @MessageMapping을 붙인 메서드에는 자동으로 앞쪽에 prefix가 붙는다

        registry.setUserDestinationPrefix("/user");
        // 특정 사용자에게 보내는 1:1 메시지 destination prefix
    }

    // WebSocket 연결을 시작하는 handshake endpoint
    // 최초 연결은 HTTP 요청으로 들어오고, 이후 WebSocket으로 upgrade된다
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = corsProperties.allowedOrigins().toArray(String[]::new);
        // 공통 CORS 설정에서 허용 Origin 목록을 가져온다

        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
        // WebSocket 연결 이후 들어오는 STOMP 메시지(CONNECT, SEND, SUBSCRIBE 등)를 검사하는 인터셉터 등록
        // handshake 이후의 메시지는 일반 HTTP 요청처럼 Servlet Filter를 매번 타지 않으므로 별도 인증/인가 처리가 필요하다
        // jwtChannelInterceptor 안쪽에서는 http요청에서 쓰는 JwtAuthenticator를 사용한다.
    }
}
