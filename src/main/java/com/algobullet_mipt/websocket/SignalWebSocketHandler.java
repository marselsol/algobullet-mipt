package com.algobullet_mipt.websocket;

import com.algobullet_mipt.entity.UserAccount;
import com.algobullet_mipt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
@Slf4j
public class SignalWebSocketHandler extends TextWebSocketHandler {

    private final UserRepository userRepository;
    private final UserSignalSessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = resolveUserId(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Пользователь не найден"));
            return;
        }

        sessionRegistry.addSession(userId, session);
        log.info("Открыт клиентский websocket сигналов user={} session={}", userId, session.getId());
        session.sendMessage(new TextMessage("""
                {"event":"connected","message":"WebSocket соединение установлено"}
                """.trim()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // В MVP клиент ничего не отправляет. Соединение используется только для доставки сигналов.
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.removeSession(session);
        log.info("Закрыт клиентский websocket сигналов session={} code={}", session.getId(), status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessionRegistry.removeSession(session);
        log.warn("Ошибка клиентского websocket session={}: {}", session.getId(), exception.getMessage());
    }

    private Long resolveUserId(WebSocketSession session) {
        if (session.getPrincipal() == null || session.getPrincipal().getName() == null) {
            return null;
        }

        String username = session.getPrincipal().getName().trim();
        return userRepository.findByUsernameIgnoreCase(username)
                .map(UserAccount::getId)
                .orElse(null);
    }
}
