package com.dopamin.omok.game.adapter.in.web;

import com.dopamin.omok.game.adapter.in.web.dto.ChangeStoneSkinRequest;
import com.dopamin.omok.game.adapter.in.web.dto.ChatMessageRequest;
import com.dopamin.omok.game.adapter.in.web.dto.GameMoveRequest;
import com.dopamin.omok.game.application.dto.ChatMessageResponse;
import com.dopamin.omok.game.application.dto.GameMoveResponse;
import com.dopamin.omok.game.application.dto.RoomResponse;
import com.dopamin.omok.game.application.port.in.ChangeStoneSkinUseCase;
import com.dopamin.omok.game.application.port.in.GetRoomUseCase;
import com.dopamin.omok.game.application.port.in.PlaceStoneUseCase;
import com.dopamin.omok.game.application.port.in.ReadyGameUseCase;
import com.dopamin.omok.game.application.port.in.SendChatMessageUseCase;
import com.dopamin.omok.game.application.port.in.StartGameUseCase;
import com.dopamin.omok.game.application.port.in.SurrenderUseCase;
import com.dopamin.omok.game.application.port.in.SwapColorsUseCase;
import com.dopamin.omok.game.application.port.out.RoomEventPublisherPort;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.principal.AuthUser;
import com.dopamin.omok.global.websocket.WebSocketSessionRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final PlaceStoneUseCase placeStoneUseCase;
    private final SurrenderUseCase surrenderUseCase;
    private final GetRoomUseCase getRoomUseCase;
    private final ReadyGameUseCase readyGameUseCase;
    private final StartGameUseCase startGameUseCase;
    private final ChangeStoneSkinUseCase changeStoneSkinUseCase;
    private final SwapColorsUseCase swapColorsUseCase;
    private final SendChatMessageUseCase sendChatMessageUseCase;
    private final WebSocketSessionRegistry sessionRegistry;
    private final RoomEventPublisherPort roomEventPublisher;

    @MessageMapping("/game/{roomCode}/move")
    @SendTo("/topic/room/{roomCode}")
    public ApiResponse<GameMoveResponse> handleMove(
            @DestinationVariable String roomCode,
            @Valid GameMoveRequest request,
            SimpMessageHeaderAccessor headerAccessor) {
        Long userId = extractUserId(headerAccessor);
        registerSession(headerAccessor, roomCode, userId);
        if (userId == null) return ApiResponse.error("인증이 필요합니다.");
        try {
            GameMoveResponse response = placeStoneUseCase.placeStone(roomCode, userId, request.row(), request.col());
            // 매 수마다 방 상태(currentTurn 포함) 브로드캐스트 — 클라이언트 턴 동기화
            RoomResponse roomStatus = getRoomUseCase.getRoom(roomCode);
            roomEventPublisher.publishStatus(roomCode, roomStatus);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.warn("WebSocket move error for room {}: {}", roomCode, e.getMessage());
            return ApiResponse.error(clientMessage(e));
        }
    }

    @MessageMapping("/game/{roomCode}/surrender")
    @SendTo("/topic/room/{roomCode}/status")
    public ApiResponse<RoomResponse> handleSurrender(
            @DestinationVariable String roomCode,
            SimpMessageHeaderAccessor headerAccessor) {
        Long userId = extractUserId(headerAccessor);
        registerSession(headerAccessor, roomCode, userId);
        if (userId == null) return ApiResponse.error("인증이 필요합니다.");
        try {
            surrenderUseCase.surrender(roomCode, userId);
            RoomResponse response = getRoomUseCase.getRoom(roomCode);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.warn("WebSocket surrender error for room {}: {}", roomCode, e.getMessage());
            return ApiResponse.error(clientMessage(e));
        }
    }

    @MessageMapping("/game/{roomCode}/ready")
    public void handleReady(
            @DestinationVariable String roomCode,
            SimpMessageHeaderAccessor headerAccessor) {
        Long userId = extractUserId(headerAccessor);
        registerSession(headerAccessor, roomCode, userId);
        if (userId == null) {
            log.warn("Unauthenticated ready for room {}", roomCode);
            return;
        }
        try {
            readyGameUseCase.readyGame(roomCode, userId);
        } catch (Exception e) {
            log.warn("WebSocket ready error for room {}: {}", roomCode, e.getMessage());
        }
    }

    @MessageMapping("/game/{roomCode}/start")
    public void handleStart(
            @DestinationVariable String roomCode,
            SimpMessageHeaderAccessor headerAccessor) {
        Long userId = extractUserId(headerAccessor);
        registerSession(headerAccessor, roomCode, userId);
        if (userId == null) {
            log.warn("Unauthenticated start for room {}", roomCode);
            return;
        }
        try {
            startGameUseCase.startGame(roomCode, userId);
        } catch (Exception e) {
            log.warn("WebSocket start error for room {}: {}", roomCode, e.getMessage());
        }
    }

    @MessageMapping("/game/{roomCode}/change-skin")
    public void handleChangeSkin(
            @DestinationVariable String roomCode,
            @Valid @Payload ChangeStoneSkinRequest request,
            SimpMessageHeaderAccessor headerAccessor) {
        Long userId = extractUserId(headerAccessor);
        registerSession(headerAccessor, roomCode, userId);
        if (userId == null) {
            log.warn("Unauthenticated change-skin for room {}", roomCode);
            return;
        }
        try {
            // 성공 시 changeStoneSkin 내부에서 방 상태를 양쪽에 브로드캐스트한다.
            changeStoneSkinUseCase.changeStoneSkin(roomCode, userId, request.itemId());
        } catch (Exception e) {
            log.warn("WebSocket change-skin error for room {}: {}", roomCode, e.getMessage());
        }
    }

    @MessageMapping("/game/{roomCode}/swap-colors")
    public void handleSwapColors(
            @DestinationVariable String roomCode,
            SimpMessageHeaderAccessor headerAccessor) {
        Long userId = extractUserId(headerAccessor);
        registerSession(headerAccessor, roomCode, userId);
        if (userId == null) {
            log.warn("Unauthenticated swap-colors for room {}", roomCode);
            return;
        }
        try {
            // 방장 여부/대기 상태 검증은 유스케이스가 하고, 성공 시 방 상태를 양쪽에 브로드캐스트한다.
            swapColorsUseCase.swapColors(roomCode, userId);
        } catch (Exception e) {
            log.warn("WebSocket swap-colors error for room {}: {}", roomCode, e.getMessage());
        }
    }

    @MessageMapping("/game/{roomCode}/chat")
    @SendTo("/topic/room/{roomCode}/chat")
    public ApiResponse<ChatMessageResponse> handleChat(
            @DestinationVariable String roomCode,
            @Valid @Payload ChatMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor) {
        AuthUser user = extractUser(headerAccessor);
        if (user == null) return ApiResponse.error("인증이 필요합니다.");
        registerSession(headerAccessor, roomCode, user.id());
        try {
            ChatMessageResponse response = sendChatMessageUseCase.sendChatMessage(
                    roomCode, user.id(), user.nickname(), request.content());
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.warn("WebSocket chat error for room {}: {}", roomCode, e.getMessage());
            return ApiResponse.error(clientMessage(e));
        }
    }

    // @Valid 페이로드 검증 실패(예: 200자 초과/빈 채팅)를 STOMP 에러 프레임 대신
    // 호출자에게 전달되는 에러 응답으로 변환한다.
    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        log.debug("WebSocket payload validation failed: {}", e.getMessage());
        return ApiResponse.error("입력값 검증에 실패했습니다.");
    }

    // JwtChannelInterceptor가 CONNECT 시 accessor.setUser(auth)로 설정한 값을 읽음
    // @AuthenticationPrincipal은 SecurityContextHolder(ThreadLocal)을 사용하므로
    // WebSocket 메시지 처리 스레드에서는 동작하지 않음
    private AuthUser extractUser(SimpMessageHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken authToken
                && authToken.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        return null;
    }

    // 도메인 예외(OmokException) 메시지는 사용자용으로 안전하므로 그대로 노출하고,
    // 그 외 일반 예외는 내부 구현 정보가 새지 않도록 일반화된 메시지로 대체한다.
    private String clientMessage(Exception e) {
        return (e instanceof OmokException) ? e.getMessage() : "요청을 처리하지 못했습니다.";
    }

    private Long extractUserId(SimpMessageHeaderAccessor accessor) {
        AuthUser user = extractUser(accessor);
        return user != null ? user.id() : null;
    }

    private void registerSession(SimpMessageHeaderAccessor accessor, String roomCode, Long userId) {
        if (accessor.getSessionId() != null && userId != null) {
            sessionRegistry.register(accessor.getSessionId(), roomCode, userId);
        }
    }
}
