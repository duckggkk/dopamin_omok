package com.dopamin.omok.global.websocket;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, String roomCode, Long userId) {
        sessions.put(sessionId, new SessionInfo(roomCode, userId));
    }

    public Optional<SessionInfo> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * 이 방에 실시간으로 접속해 있는 클라이언트가 한 명이라도 있는지.
     * 방치된 방 정리 스케줄러가 "진짜 아무도 없는 방"만 닫도록 판별할 때 쓴다.
     * <p>
     * 값 전체를 훑지만 항목 수가 '현재 동시접속자 수'라 매우 작고,
     * 호출도 정리 주기(수십 분)에 한 번뿐이라 인덱스를 따로 두지 않는다.
     * <p>
     * ⚠️ 이 레지스트리는 서버 메모리에만 있다. 서버가 재시작하면 비어 있으므로
     * 재시작 직후에는 모든 방이 "접속자 없음"으로 보인다 — 이는 의도된 동작이다
     * (재시작으로 WebSocket 연결이 실제로 전부 끊겼기 때문). 다만 멀티서버로 확장하면
     * 다른 인스턴스의 접속자를 알 수 없으므로 공유 저장소(Redis)로 옮겨야 한다.
     */
    public boolean hasActiveSession(String roomCode) {
        if (roomCode == null) return false;
        return sessions.values().stream().anyMatch(info -> roomCode.equals(info.roomCode()));
    }

    public record SessionInfo(String roomCode, Long userId) {}
}
