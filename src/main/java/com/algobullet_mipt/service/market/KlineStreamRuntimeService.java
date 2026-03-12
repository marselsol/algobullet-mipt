package com.algobullet_mipt.service.market;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.domain.market.model.KlineStreamChannel;
import com.algobullet_mipt.domain.market.model.KlineStreamUpdate;
import com.algobullet_mipt.domain.market.port.KlineStreamPort;
import com.algobullet_mipt.domain.market.port.MarketDataPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class KlineStreamRuntimeService {

    private static final int MAX_CANDLES_PER_CHANNEL = 500;

    private final KlineStreamPort klineStreamPort;
    private final MarketDataPort marketDataPort;

    private final ConcurrentMap<KlineStreamChannel, ChannelState> channels = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ListenerRegistration> listeners = new ConcurrentHashMap<>();

    public UUID subscribe(String symbol, String timeframe, Consumer<KlineStreamUpdate> listener) {
        return subscribe(symbol, timeframe, 0, listener);
    }

    public UUID subscribe(String symbol, String timeframe, int historyLimit, Consumer<KlineStreamUpdate> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }

        KlineStreamChannel channel = new KlineStreamChannel(symbol, timeframe);
        ensureRecentCandles(symbol, timeframe, historyLimit);
        UUID listenerId = UUID.randomUUID();
        ChannelState state = channels.computeIfAbsent(channel, ignored -> new ChannelState());

        boolean shouldSubscribe = false;
        synchronized (state) {
            if (state.listenerIds.isEmpty()) {
                shouldSubscribe = true;
            }
            state.listenerIds.add(listenerId);
        }

        listeners.put(listenerId, new ListenerRegistration(channel, listener));

        if (shouldSubscribe) {
            try {
                klineStreamPort.subscribe(channel.symbol(), channel.timeframe());
                log.info("Подписка на поток свечей: {} {}", channel.symbol(), channel.timeframe());
            } catch (Exception ex) {
                unsubscribe(listenerId);
                throw ex;
            }
        }

        return listenerId;
    }

    public void ensureRecentCandles(String symbol, String timeframe, int limit) {
        if (limit <= 0) {
            return;
        }

        KlineStreamChannel channel = new KlineStreamChannel(symbol, timeframe);
        ChannelState state = channels.computeIfAbsent(channel, ignored -> new ChannelState());

        synchronized (state) {
            if (state.candles.size() >= limit) {
                return;
            }

            try {
                List<KlineCandle> candles = marketDataPort.getRecentKlines(symbol, timeframe, limit);
                for (KlineCandle candle : candles) {
                    upsertCandle(state.candles, candle);
                }
                log.info("Подгружена история свечей: {} {} count={}", symbol, timeframe, candles.size());
            } catch (Exception ex) {
                log.warn("Не удалось подгрузить историю свечей {} {}: {}", symbol, timeframe, ex.getMessage());
            }
        }
    }

    public void unsubscribe(UUID listenerId) {
        if (listenerId == null) {
            return;
        }

        ListenerRegistration registration = listeners.remove(listenerId);
        if (registration == null) {
            return;
        }

        KlineStreamChannel channel = registration.channel();
        ChannelState state = channels.get(channel);
        if (state == null) {
            return;
        }

        boolean shouldUnsubscribe = false;
        synchronized (state) {
            state.listenerIds.remove(listenerId);
            if (state.listenerIds.isEmpty()) {
                shouldUnsubscribe = true;
            }
        }

        if (shouldUnsubscribe) {
            try {
                klineStreamPort.unsubscribe(channel.symbol(), channel.timeframe());
                log.info("Отписка от потока свечей: {} {}", channel.symbol(), channel.timeframe());
            } catch (Exception ex) {
                log.warn("Ошибка отписки от потока свечей {} {}: {}", channel.symbol(), channel.timeframe(), ex.getMessage());
            }
        }

        if (shouldUnsubscribe) {
            channels.remove(channel, state);
        }
    }

    public void publish(String symbol, String timeframe, KlineCandle candle, boolean closed) {
        if (candle == null) {
            return;
        }

        KlineStreamChannel channel = new KlineStreamChannel(symbol, timeframe);
        ChannelState state = channels.computeIfAbsent(channel, ignored -> new ChannelState());
        KlineStreamUpdate update = new KlineStreamUpdate(channel, candle, closed, Instant.now());

        List<Consumer<KlineStreamUpdate>> listenersToNotify;
        synchronized (state) {
            upsertCandle(state.candles, candle);
            state.lastUpdate = update;

            listenersToNotify = new ArrayList<>(state.listenerIds.size());
            for (UUID listenerId : state.listenerIds) {
                ListenerRegistration registration = listeners.get(listenerId);
                if (registration != null) {
                    listenersToNotify.add(registration.listener());
                }
            }
        }

        for (Consumer<KlineStreamUpdate> listener : listenersToNotify) {
            try {
                listener.accept(update);
            } catch (Exception ex) {
                log.warn("Слушатель потока свечей завершился с ошибкой для {} {}: {}",
                        channel.symbol(), channel.timeframe(), ex.getMessage());
            }
        }
    }

    public List<KlineCandle> getRecentCandles(String symbol, String timeframe, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        KlineStreamChannel channel = new KlineStreamChannel(symbol, timeframe);
        ChannelState state = channels.get(channel);
        if (state == null) {
            return List.of();
        }

        synchronized (state) {
            int skip = Math.max(0, state.candles.size() - limit);
            return state.candles.stream().skip(skip).toList();
        }
    }

    public Optional<KlineStreamUpdate> getLastUpdate(String symbol, String timeframe) {
        KlineStreamChannel channel = new KlineStreamChannel(symbol, timeframe);
        ChannelState state = channels.get(channel);
        if (state == null) {
            return Optional.empty();
        }
        synchronized (state) {
            return Optional.ofNullable(state.lastUpdate);
        }
    }

    public Set<KlineStreamChannel> getActiveChannels() {
        return channels.entrySet().stream()
                .filter(entry -> hasListeners(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean hasListeners(ChannelState state) {
        synchronized (state) {
            return !state.listenerIds.isEmpty();
        }
    }

    private void upsertCandle(Deque<KlineCandle> candles, KlineCandle candle) {
        KlineCandle last = candles.peekLast();

        // Если пришел апдейт той же свечи (тот же openTime), заменяем последнюю запись.
        if (last != null && last.openTime().equals(candle.openTime())) {
            candles.removeLast();
        }

        candles.addLast(candle);
        while (candles.size() > MAX_CANDLES_PER_CHANNEL) {
            candles.removeFirst();
        }
    }

    private static final class ChannelState {
        private final Deque<KlineCandle> candles = new ArrayDeque<>();
        private final Set<UUID> listenerIds = new java.util.HashSet<>();
        private KlineStreamUpdate lastUpdate;
    }

    private record ListenerRegistration(
            KlineStreamChannel channel,
            Consumer<KlineStreamUpdate> listener
    ) {
    }
}
