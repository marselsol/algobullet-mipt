package com.algobullet_mipt.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserSignalSessionRegistry {

    private final Map<Long, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();

    public void addSession(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }

        sessionsByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    public void removeSession(WebSocketSession session) {
        if (session == null) {
            return;
        }

        sessionsByUserId.values().forEach(sessions -> sessions.remove(session));
        sessionsByUserId.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void sendToUser(Long userId, TextMessage message) throws IOException {
        if (userId == null || message == null) {
            return;
        }

        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        for (WebSocketSession session : sessions) {
            if (session == null || !session.isOpen()) {
                removeSession(session);
                continue;
            }
            synchronized (session) {
                session.sendMessage(message);
            }
        }
    }

    public int getSessionCount(Long userId) {
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        return sessions == null ? 0 : sessions.size();
    }
}
