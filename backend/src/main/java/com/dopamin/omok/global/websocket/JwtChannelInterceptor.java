package com.dopamin.omok.global.websocket;

import com.dopamin.omok.game.application.port.out.LoadGamePlayerPort;
import com.dopamin.omok.game.application.port.out.LoadRoomPort;
import com.dopamin.omok.game.domain.GamePlayer;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.security.jwt.JwtAuthConstants;
import com.dopamin.omok.global.security.jwt.JwtAuthenticator;
import com.dopamin.omok.global.security.principal.AuthUser;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
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
    // 광장은 SUBSCRIBE(/topic)와 SEND(/app)의 허용 규칙이 다르므로 패턴을 분리한다.
    private static final Pattern PLAZA_TOPIC_PATTERN = Pattern.compile("^/topic/plaza/([^/]+)(?:/.*)?$");
    private static final Pattern PLAZA_APP_PATTERN = Pattern.compile("^/app/plaza/([^/]+)/[^/]+$");
    /**
     * 광장 채널 ID 는 서버(PlazaSessionManager.allocate)가 {@code plaza-<번호>} 형식으로만 발급한다.
     * 클라이언트가 임의 문자열을 보내 채널을 무한 생성하지 못하도록 여기서 형식부터 막는다.
     * <p>
     * 실제 "그 채널이 존재하는지"는 {@code PlazaSessionManager} 가 검증한다.
     * 여기서 PlazaSessionManager 를 주입하면
     * WebSocketConfig → JwtChannelInterceptor → PlazaSessionManager → PlazaEventPublisher
     * → SimpMessagingTemplate → (WebSocketConfig) 로 순환 참조가 생기기 때문이다.
     */
    private static final Pattern PLAZA_CHANNEL_ID_PATTERN = Pattern.compile("^plaza-\\d{1,9}$");

    private final JwtAuthenticator jwtAuthenticator;
    private final LoadRoomPort loadRoomPort;
    private final LoadGamePlayerPort loadGamePlayerPort;
    private final LoadUserPort loadUserPort;

    
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

            // CONNECT 단계에서 유효한 JWT(서명 + 만료)가 없으면 연결 자체를 거부한다.
            // 미인증 클라이언트가 임의의 방 토픽(/topic/room/{code})을 구독해
            // 타인의 수순·채팅을 열람하는 것을 차단한다.
            // 단 tokenVersion 은 검사하지 않으므로(JwtAuthenticator 주석 참고)
            // 로그아웃 직후의 토큰이라도 만료 전이면 통과한다.
            UsernamePasswordAuthenticationToken authentication = jwtAuthenticator.authenticate(token)
                    .orElseThrow(() -> {
                        log.warn("WebSocket CONNECT 거부 — 유효한 인증 토큰 없음");
                        return new MessagingException("WebSocket 인증에 실패했습니다.");
                    });

            // 탈퇴한 계정 차단. JWT 검증은 stateless 라 탈퇴 직전에 발급된 access token 이
            // 만료(30분)까지 살아 있는데, 광장(/plaza)은 REST 와 달리 DB 를 거치지 않고
            // 토큰 claim 만으로 동작해서 탈퇴자가 옛 닉네임으로 돌아다닐 수 있다.
            // CONNECT 는 소켓당 한 번뿐이라 여기서 한 번만 확인하면 WS 경로 전체가 막힌다.
            if (authentication.getPrincipal() instanceof AuthUser user
                    && loadUserPort.findById(user.id()).isEmpty()) {
                log.warn("WebSocket CONNECT 거부 — 존재하지 않거나 탈퇴한 계정 userId={}", user.id());
                throw new MessagingException("WebSocket 인증에 실패했습니다.");
            }

            //WebSocket/STOMP 세션에 인증자 정보 세팅 (서버에)
            accessor.setUser(authentication);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            authorizeSend(accessor);
        }
        return message;
    }

    /**
     * SUBSCRIBE(구독) 인가 — "무엇을 받아볼 수 있는가"를 검증한다.
     * <p>
     * 허용 목록(그 외는 전부 거부):
     * <ul>
     *   <li>{@code /topic/room/{code}/...} — 그 방의 멤버(플레이어·관전자)만</li>
     *   <li>{@code /topic/plaza/{channelId}/...} — 서버 발급 형식의 채널 ID만</li>
     *   <li>{@code /user/...} — 본인 전용 큐(Spring 이 세션별로 격리해주므로 안전)</li>
     * </ul>
     */
    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            // STOMP 스펙상 SUBSCRIBE 프레임엔 destination 이 필수 — 없으면 비정상 프레임이므로 거부
            throw reject("SUBSCRIBE destination 이 없습니다.");
        }

        Long userId = requireUserId(accessor);

        Matcher roomTopic = ROOM_TOPIC_PATTERN.matcher(destination);
        if (roomTopic.matches()) {
            requireRoomMember(roomTopic.group(1), userId);
            return;
        }

        Matcher plazaTopic = PLAZA_TOPIC_PATTERN.matcher(destination);
        if (plazaTopic.matches()) {
            // 채널 ID 형식이 서버 발급 규칙과 다르면 거부한다.
            // (방/피지컬과 달리 광장은 누구나 입장 가능하므로 멤버십 검증은 하지 않지만,
            //  임의 채널명으로 세션을 만들어내는 것은 막아야 한다)
            requirePlazaChannelFormat(plazaTopic.group(1));
            return;
        }

        if (destination.startsWith("/user/")) {
            return;
        }

        throw reject("허용되지 않은 구독 destination 입니다.");
    }

    /**
     * SEND(전송) 인가 — "무엇을 보낼 수 있는가"를 검증한다.
     * <p>
     * 클라이언트의 SEND 는 반드시 {@code /app/**}(= @MessageMapping 컨트롤러)로만 들어와야 한다.
     * {@code /topic/**}, {@code /queue/**}, {@code /user/**} 로의 직접 SEND 를 허용하면
     * 컨트롤러의 검증/게임 규칙을 건너뛰고 브로커를 통해 구독자 전원에게
     * 위조 메시지(가짜 게임 종료, 닉네임 사칭 채팅 등)를 뿌릴 수 있기 때문에 전부 거부한다.
     * (Spring Security 공식 문서도 브로커 destination 으로의 직접 SEND 차단을 권고)
     */
    private void authorizeSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw reject("SEND destination 이 없습니다.");
        }

        Long userId = requireUserId(accessor);

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

        Matcher plazaApp = PLAZA_APP_PATTERN.matcher(destination);
        if (plazaApp.matches()) {
            requirePlazaChannelFormat(plazaApp.group(1));
            return;
        }

        // 위 화이트리스트에 없는 모든 SEND 는 거부 — /topic, /queue, /user 직접 전송 차단 포함
        throw reject("허용되지 않은 전송 destination 입니다.");
    }

    private Long requireUserId(StompHeaderAccessor accessor) {
        Long userId = extractUserId(accessor);
        if (userId == null) {
            throw reject("WebSocket 인증이 필요합니다.");
        }
        return userId;
    }

    private void requirePlazaChannelFormat(String channelId) {
        if (!PLAZA_CHANNEL_ID_PATTERN.matcher(channelId).matches()) {
            throw reject("허용되지 않은 광장 채널입니다.");
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
