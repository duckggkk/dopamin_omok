package com.dopamin.omok.global.websocket;

import com.dopamin.omok.game.application.port.out.LoadGamePlayerPort;
import com.dopamin.omok.game.application.port.out.LoadRoomPort;
import com.dopamin.omok.game.domain.GamePlayer;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.security.jwt.JwtAuthConstants;
import com.dopamin.omok.global.security.jwt.JwtAuthenticator;
import com.dopamin.omok.global.security.principal.AuthUser;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 웹소켓 전용 인증인가 인터셉터 클래스
// HTTP 요청으로  WebSocket 핸드셰이크한 후 바로 CONNTECT전송 (여기서부터 http요청아님)
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_TOPIC_PATTERN = Pattern.compile("^/topic/room/([^/]+)(?:/.*)?$");
    private static final Pattern GAME_APP_PATTERN = Pattern.compile("^/app/game/([^/]+)/[^/]+$");
    private static final Pattern PHYSICAL_APP_PATTERN = Pattern.compile("^/app/physical/([^/]+)/[^/]+$");
    private static final Pattern PLAZA_PATTERN = Pattern.compile("^/(?:topic|app)/plaza/([^/]+)(?:/.*)?$");

    private final JwtAuthenticator jwtAuthenticator;
    private final LoadRoomPort loadRoomPort;
    private final LoadGamePlayerPort loadGamePlayerPort;

    
    // 모든 inbound STOMP 메시지를 받음
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // “STOMP 명령어로 판단할 수 없는 프레임이므로 통과시킨다 (다음 인터셉터에서 판단)
        // 예외를 안 던지는 이유는 STOMP heartbeat나 프레임워크 내부 메시지처럼 인증 대상이 아닌 메시지까지 막아서
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 헤더 추출
            String authHeader = accessor.getFirstNativeHeader(JwtAuthConstants.AUTHORIZATION_HEADER);

            // 토큰 추출
            String token = (authHeader != null && authHeader.startsWith(JwtAuthConstants.BEARER_PREFIX))
                    ? authHeader.substring(JwtAuthConstants.BEARER_PREFIX.length())
                    : null;

            // CONNECT 단계에서 유효한 JWT(서명 + tokenVersion 일치)가 없으면 연결 자체를 거부한다.
            // 미인증 클라이언트가 임의의 방 토픽(/topic/room/{code})을 구독해
            // 타인의 수순·채팅을 열람하는 것을 차단한다(로그아웃 토큰도 거부).
            UsernamePasswordAuthenticationToken authentication = jwtAuthenticator.authenticate(token)
                    .orElseThrow(() -> {
                        log.warn("WebSocket CONNECT 거부 — 유효한 인증 토큰 없음");
                        return new MessagingException("WebSocket 인증에 실패했습니다.");
                    });
            //WebSocket/STOMP 세션에 인증자 정보 세팅 (서버에)
            accessor.setUser(authentication);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand()) || StompCommand.SEND.equals(accessor.getCommand())) {
            authorizeDestination(accessor);
        }
        return message;
    }

    // 받는사람 검증
    private void authorizeDestination(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return;

        Long userId = extractUserId(accessor);
        if (userId == null) {
            throw new MessagingException("WebSocket 인증이 필요합니다.");
        }

        Matcher roomTopic = ROOM_TOPIC_PATTERN.matcher(destination);
        if (roomTopic.matches()) {
            requireRoomMember(roomTopic.group(1), userId);
            return;
        }

        Matcher gameApp = GAME_APP_PATTERN.matcher(destination);
        if (gameApp.matches()) {
            requireRoomMember(gameApp.group(1), userId);
            return;
        }

        Matcher physicalApp = PHYSICAL_APP_PATTERN.matcher(destination);
        if (physicalApp.matches()) {
            requireRoomParticipant(physicalApp.group(1), userId);
            return;
        }

        Matcher plaza = PLAZA_PATTERN.matcher(destination);
        if (plaza.matches() || destination.startsWith("/user/")) {
            return;
        }

        if (destination.startsWith("/topic/") || destination.startsWith("/app/")) {
            throw new MessagingException("허용되지 않은 WebSocket destination 입니다.");
        }
    }

    private void requireRoomMember(String roomCode, Long userId) {
        Room room = loadRoomPort.findByRoomCode(roomCode)
                .orElseThrow(() -> reject(ErrorCode.ROOM_NOT_FOUND.getMessage()));
        loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> reject(ErrorCode.NOT_IN_ROOM.getMessage()));
    }

    private void requireRoomParticipant(String roomCode, Long userId) {
        Room room = loadRoomPort.findByRoomCode(roomCode)
                .orElseThrow(() -> reject(ErrorCode.ROOM_NOT_FOUND.getMessage()));
        loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId)
                .filter(GamePlayer::isParticipant)
                .orElseThrow(() -> reject(ErrorCode.NOT_GAME_PARTICIPANT.getMessage()));
    }

    private MessagingException reject(String message) {
        return new MessagingException(message);
    }

    private Long extractUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken authToken
                && authToken.getPrincipal() instanceof AuthUser user) {
            return user.id();
        }
        return null;
    }
}
