package com.dopamin.omok.plaza.adapter.in.web;

import com.dopamin.omok.game.adapter.in.web.dto.ChatMessageRequest;
import com.dopamin.omok.game.application.dto.ChatMessageResponse;
import com.dopamin.omok.global.security.principal.AuthUser;
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
        AuthUser user = extractUser(accessor);
        if (user == null) return;
        // 게스트(비회원)는 아바타 꾸미기 불가 → 외형을 무시하고 기본 외형으로 입장시킨다.
        boolean guest = user.isGuest();
        manager.join(channelId, accessor.getSessionId(), user.id(),
                user.publicId().toString(), user.nickname(),
                guest ? null : (request != null ? request.appearance() : null));
    }

    @MessageMapping("/plaza/{channelId}/input")
    public void handleInput(
            @DestinationVariable String channelId,
            @Valid @Payload PlazaInputRequest request,
            SimpMessageHeaderAccessor accessor) {
        AuthUser user = extractUser(accessor);
        if (user == null) return;
        manager.applyInput(channelId, user.id(), request.type(), request.direction());
    }

    @MessageMapping("/plaza/{channelId}/leave")
    public void handleLeave(
            @DestinationVariable String channelId,
            SimpMessageHeaderAccessor accessor) {
        AuthUser user = extractUser(accessor);
        if (user == null) return;
        manager.leave(channelId, user.id());
    }

    /** 드레스룸: 실시간 외형 변경(전원에게 즉시 반영). Phase 1 은 소유권 검증 없이 중계. */
    @MessageMapping("/plaza/{channelId}/appearance")
    public void handleAppearance(
            @DestinationVariable String channelId,
            @Payload PlazaJoinRequest request,
            SimpMessageHeaderAccessor accessor) {
        AuthUser user = extractUser(accessor);
        if (user == null || request == null) return;
        if (user.isGuest()) return; // 게스트는 아바타 꾸미기(드레스룸) 불가
        manager.updateAppearance(channelId, user.id(), request.appearance());
    }

    @MessageMapping("/plaza/{channelId}/chat")
    public void handleChat(
            @DestinationVariable String channelId,
            @Valid @Payload ChatMessageRequest request,
            SimpMessageHeaderAccessor accessor) {
        AuthUser user = extractUser(accessor);
        if (user == null || !manager.isMember(channelId, user.id())) return;
        String nickname = user.nickname();
        // 광장 채팅엔 색/관전 개념이 없으므로 color=null, spectator=true 로 ChatMessageResponse 를 재사용한다.
        ChatMessageResponse response = new ChatMessageResponse(nickname, null, true, request.content(), LocalDateTime.now());
        eventPublisher.publishChat(channelId, response);
    }

    // JwtChannelInterceptor 가 CONNECT 시 accessor.setUser(auth) 로 설정한 값을 읽는다.
    private AuthUser extractUser(SimpMessageHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthUser details) {
            return details;
        }
        return null;
    }
}
