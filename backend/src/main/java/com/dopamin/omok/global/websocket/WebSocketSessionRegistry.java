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

    public record SessionInfo(String roomCode, Long userId) {}
}
