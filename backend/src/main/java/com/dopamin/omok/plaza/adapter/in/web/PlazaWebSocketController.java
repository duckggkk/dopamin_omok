package com.dopamin.omok.plaza.adapter.in.web;

import com.dopamin.omok.game.adapter.in.web.dto.ChatMessageRequest;
import com.dopamin.omok.game.application.dto.ChatMessageResponse;
import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import com.dopamin.omok.plaza.adapter.in.web.dto.PlazaInputRequest;
import com.dopamin.omok.plaza.adapter.in.web.dto.PlazaJoinRequest;
import com.dopamin.omok.plaza.application.PlazaSessionManager;
import com.dopamin.omok.plaza.application.port.out.PlazaEventPublisherPort;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

/**
 * 광장 실시간 입장/입력/채팅 수신. 스냅샷 브로드캐스트는 SessionManager 가 담당(여기엔 @SendTo 없음).
 * 인증된 userId 만 전달하며, 참가자 검증/경계는 SessionManager(서버 권위)에서 강제한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PlazaWebSocketController {

    private final PlazaSessionManager manager;
    private final PlazaEventPublisherPort eventPublisher;

    @MessageMapping("/plaza/{channelId}/join")
    public void handleJoin(
            @DestinationVariable String channelId,
            @Payload PlazaJoinRequest request,
            SimpMessageHeaderAccessor accessor) {
        CustomUserDetails user = extractUser(accessor);
        if (user == null) return;
        manager.join(channelId, accessor.getSessionId(), user.getId(),
                user.getUser().getPublicId().toString(), user.getUser().getNickname(),
                request != null ? request.appearance() : null);
    }

    @MessageMapping("/plaza/{channelId}/input")
    public void handleInput(
            @DestinationVariable String channelId,
            @Valid @Payload PlazaInputRequest request,
            SimpMessageHeaderAccessor accessor) {
        CustomUserDetails user = extractUser(accessor);
        if (user == null) return;
        manager.applyInput(channelId, user.getId(), request.type(), request.direction());
    }

    @MessageMapping("/plaza/{channelId}/leave")
    public void handleLeave(
            @DestinationVariable String channelId,
            SimpMessageHeaderAccessor accessor) {
        CustomUserDetails user = extractUser(accessor);
        if (user == null) return;
        manager.leave(channelId, user.getId());
    }

    /** 드레스룸: 실시간 외형 변경(전원에게 즉시 반영). Phase 1 은 소유권 검증 없이 중계. */
    @MessageMapping("/plaza/{channelId}/appearance")
    public void handleAppearance(
            @DestinationVariable String channelId,
            @Payload PlazaJoinRequest request,
            SimpMessageHeaderAccessor accessor) {
        CustomUserDetails user = extractUser(accessor);
        if (user == null || request == null) return;
        manager.updateAppearance(channelId, user.getId(), request.appearance());
    }

    @MessageMapping("/plaza/{channelId}/chat")
    public void handleChat(
            @DestinationVariable String channelId,
            @Valid @Payload ChatMessageRequest request,
            SimpMessageHeaderAccessor accessor) {
        CustomUserDetails user = extractUser(accessor);
        if (user == null || !manager.isMember(channelId, user.getId())) return;
        String nickname = user.getUser().getNickname();
        // 광장 채팅엔 색/관전 개념이 없으므로 color=null, spectator=true 로 ChatMessageResponse 를 재사용한다.
        ChatMessageResponse response = new ChatMessageResponse(nickname, null, true, request.content(), LocalDateTime.now());
        eventPublisher.publishChat(channelId, response);
    }

    // JwtChannelInterceptor 가 CONNECT 시 accessor.setUser(auth) 로 설정한 값을 읽는다.
    private CustomUserDetails extractUser(SimpMessageHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof CustomUserDetails details) {
            return details;
        }
        return null;
    }
}
