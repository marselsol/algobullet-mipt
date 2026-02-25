package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.domain.market.port.KlineStreamPort;
import com.algobullet_mipt.service.market.KlineStreamRuntimeService;
import com.bybit.api.client.domain.websocket_message.public_channel.KlineData;
import com.bybit.api.client.domain.websocket_message.public_channel.WebSocketKlineMessage;
import com.bybit.api.client.websocket.impl.WebsocketStreamClientImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.WebSocket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(
        prefix = "app.features",
        name = "use-real-market-data",
        havingValue = "true"
)
@Slf4j
public class BybitKlineStreamPort implements KlineStreamPort {

    private static final String DEFAULT_WS_PUBLIC_LINEAR_URL = "wss://stream.bybit.com";
    private static final String DEFAULT_WS_PUBLIC_LINEAR_TESTNET_URL = "wss://stream-testnet.bybit.com";
    private static final String WS_PUBLIC_LINEAR_PATH = "/v5/public/linear";

    private final KlineStreamRuntimeService runtimeService;
    private final ObjectMapper objectMapper;
    private final boolean testnet;
    private final long timeoutMs;
    private final String logLevel;
    private final String wsBaseUrl;
    private final ConcurrentMap<String, ConnectionState> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService healthcheckExecutor = Executors.newSingleThreadScheduledExecutor();

    public BybitKlineStreamPort(
            @Lazy KlineStreamRuntimeService runtimeService,
            ObjectMapper objectMapper,
            @Value("${app.bybit.testnet:false}") boolean testnet,
            @Value("${app.bybit.timeout-ms:5000}") long timeoutMs,
            @Value("${app.bybit.log-level:info}") String logLevel,
            @Value("${app.bybit.ws-public-linear-url:" + DEFAULT_WS_PUBLIC_LINEAR_URL + "}") String prodWsBaseUrl,
            @Value("${app.bybit.ws-public-linear-testnet-url:" + DEFAULT_WS_PUBLIC_LINEAR_TESTNET_URL + "}") String testnetWsBaseUrl
    ) {
        this.runtimeService = runtimeService;
        this.objectMapper = objectMapper;
        this.testnet = testnet;
        this.timeoutMs = timeoutMs;
        this.logLevel = logLevel;
        this.wsBaseUrl = testnet ? testnetWsBaseUrl : prodWsBaseUrl;

        healthcheckExecutor.scheduleAtFixedRate(this::checkConnectionsHealth, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public void subscribe(String symbol, String timeframe) {
        String normalizedSymbol = normalizeSymbol(symbol);
        String normalizedTimeframe = normalizeTimeframe(timeframe);
        String channelKey = channelKey(normalizedSymbol, normalizedTimeframe);

        connections.compute(channelKey, (key, existing) -> {
            if (existing != null) {
                return existing;
            }
            ConnectionState state = new ConnectionState(normalizedSymbol, normalizedTimeframe);
            openConnection(state);
            return state;
        });
    }

    @Override
    public void unsubscribe(String symbol, String timeframe) {
        String normalizedSymbol = normalizeSymbol(symbol);
        String normalizedTimeframe = normalizeTimeframe(timeframe);
        String channelKey = channelKey(normalizedSymbol, normalizedTimeframe);

        ConnectionState removed = connections.remove(channelKey);
        if (removed != null) {
            closeConnection(removed, "Отписка");
        }
    }

    @PreDestroy
    public void shutdown() {
        healthcheckExecutor.shutdownNow();
        for (ConnectionState state : connections.values()) {
            closeConnection(state, "Завершение приложения");
        }
        connections.clear();
    }

    private void openConnection(ConnectionState state) {
        synchronized (state.monitor) {
            if (state.closed) {
                return;
            }
            if (state.webSocket != null) {
                return;
            }

            String topic = topicFor(state.symbol, state.timeframe);
            WebsocketStreamClientImpl client = new WebsocketStreamClientImpl(
                    "",
                    "",
                    wsBaseUrl,
                    pingIntervalSeconds(),
                    "-1",
                    false,
                    logLevel,
                    null
            );
            client.setMessageHandler(message -> handleMessage(state, message));

            WebSocket webSocket = client.getPublicChannelStream(Collections.singletonList(topic), WS_PUBLIC_LINEAR_PATH);
            state.client = client;
            state.webSocket = webSocket;
            state.lastMessageAt = System.currentTimeMillis();

            log.info("Открыт Bybit WS канал свечей: {} {} ({})", state.symbol, state.timeframe, topic);
        }
    }

    private void handleMessage(ConnectionState state, String message) {
        state.lastMessageAt = System.currentTimeMillis();

        if (message == null || !message.contains("\"topic\"") || !message.contains("kline.")) {
            return;
        }

        try {
            WebSocketKlineMessage wsMessage = objectMapper.readValue(message, WebSocketKlineMessage.class);
            if (wsMessage.getTopic() == null || wsMessage.getData() == null || wsMessage.getData().isEmpty()) {
                return;
            }

            for (KlineData item : wsMessage.getData()) {
                if (item == null || item.getStart() == null) {
                    continue;
                }
                KlineCandle candle = toCandle(item);
                boolean closed = Boolean.TRUE.equals(item.getConfirm());
                runtimeService.publish(state.symbol, state.timeframe, candle, closed);
            }
        } catch (Exception ex) {
            log.debug("Не удалось обработать сообщение Bybit WS для {} {}: {}",
                    state.symbol, state.timeframe, ex.getMessage());
        }
    }

    private void checkConnectionsHealth() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ConnectionState> entry : connections.entrySet()) {
            ConnectionState state = entry.getValue();
            if (state == null || state.closed) {
                continue;
            }

            long elapsed = now - state.lastMessageAt;
            if (elapsed > Math.max(timeoutMs * 6, 90_000L)) {
                log.warn("Нет сообщений по каналу {} {} уже {} мс, переподключаю",
                        state.symbol, state.timeframe, elapsed);
                reconnect(state);
            }
        }
    }

    private void reconnect(ConnectionState state) {
        synchronized (state.monitor) {
            if (state.closed) {
                return;
            }
            closeConnectionInternal(state, "Переподключение");
            openConnection(state);
        }
    }

    private void closeConnection(ConnectionState state, String reason) {
        synchronized (state.monitor) {
            state.closed = true;
            closeConnectionInternal(state, reason);
        }
    }

    private void closeConnectionInternal(ConnectionState state, String reason) {
        WebSocket socket = state.webSocket;
        state.webSocket = null;
        state.client = null;

        if (socket != null) {
            try {
                socket.close(1000, reason);
            } catch (Exception ex) {
                log.debug("Ошибка закрытия Bybit WS канала {} {}: {}",
                        state.symbol, state.timeframe, ex.getMessage());
            }
        }
    }

    private KlineCandle toCandle(KlineData item) {
        return new KlineCandle(
                Instant.ofEpochMilli(item.getStart()),
                parseDecimal(item.getOpen()),
                parseDecimal(item.getHigh()),
                parseDecimal(item.getLow()),
                parseDecimal(item.getClose()),
                parseDecimal(item.getVolume()),
                parseDecimal(item.getTurnover())
        );
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private int pingIntervalSeconds() {
        long seconds = Math.max(15L, timeoutMs / 1000L);
        return (int) Math.min(seconds, Integer.MAX_VALUE);
    }

    private String topicFor(String symbol, String timeframe) {
        return "kline.%s.%s".formatted(mapToBybitWsInterval(timeframe), symbol);
    }

    private String mapToBybitWsInterval(String timeframe) {
        return switch (timeframe) {
            case "1m" -> "1";
            case "3m" -> "3";
            case "5m" -> "5";
            case "15m" -> "15";
            case "30m" -> "30";
            case "1h" -> "60";
            case "2h" -> "120";
            case "4h" -> "240";
            case "6h" -> "360";
            case "12h" -> "720";
            case "1d" -> "D";
            case "1w" -> "W";
            case "1M" -> "M";
            default -> throw new IllegalArgumentException("Неподдерживаемый timeframe для WS: " + timeframe);
        };
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("Symbol must not be null");
        }
        String normalized = symbol.trim()
                .toUpperCase(Locale.ROOT)
                .replace("/", "")
                .replace("-", "")
                .replace("_", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be blank");
        }
        return normalized;
    }

    private String normalizeTimeframe(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            throw new IllegalArgumentException("Timeframe must not be blank");
        }
        return timeframe.trim();
    }

    private String channelKey(String symbol, String timeframe) {
        return symbol + "|" + timeframe;
    }

    private static final class ConnectionState {
        private final Object monitor = new Object();
        private final String symbol;
        private final String timeframe;
        private volatile WebsocketStreamClientImpl client;
        private volatile WebSocket webSocket;
        private volatile long lastMessageAt = System.currentTimeMillis();
        private volatile boolean closed;

        private ConnectionState(String symbol, String timeframe) {
            this.symbol = symbol;
            this.timeframe = timeframe;
        }
    }
}
