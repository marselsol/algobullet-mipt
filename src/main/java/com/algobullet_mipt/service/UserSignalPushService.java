package com.algobullet_mipt.service;

import com.algobullet_mipt.model.Signal;
import com.algobullet_mipt.websocket.SignalMessage;
import com.algobullet_mipt.websocket.UserSignalSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSignalPushService {

    private static final int REPLAY_LIMIT = 20;

    private final UserSignalSessionRegistry sessionRegistry;
    private final SignalHistoryService signalHistoryService;

    public void pushSignal(Long userId, Signal signal, String source, String timeframe) {
        if (userId == null || signal == null) {
            return;
        }

        SignalMessage message = SignalMessage.from(signal, source, timeframe);
        try {
            sessionRegistry.sendToUser(userId, new TextMessage(message.toJson()));
        } catch (Exception ex) {
            log.warn("Не удалось отправить сигнал в клиентский websocket user={} {} {}: {}",
                    userId, signal.symbol(), signal.type(), ex.getMessage());
        }
    }

    public void replayRecentSignals(Long userId) {
        if (userId == null) {
            return;
        }

        for (SignalHistoryService.StoredSignal storedSignal : signalHistoryService.getRecentSignals(userId, REPLAY_LIMIT)) {
            pushSignal(userId, storedSignal.signal(), storedSignal.source(), storedSignal.timeframe());
        }
    }
}
