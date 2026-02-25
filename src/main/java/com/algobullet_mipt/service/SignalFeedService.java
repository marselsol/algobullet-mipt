package com.algobullet_mipt.service;

import com.algobullet_mipt.domain.signal.port.SignalPort;
import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.model.Signal;
import com.algobullet_mipt.service.ema.EmaStreamSignalService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SignalFeedService {

    private final SignalPort signalPort;
    private final ObjectProvider<EmaStreamSignalService> emaStreamSignalServiceProvider;
    private final ObjectProvider<SignalHistoryService> signalHistoryServiceProvider;

    public List<Signal> buildFeed(PumpSettings pump, EmaSettings ema) {
        List<Signal> result = new ArrayList<>();

        SignalHistoryService signalHistoryService = signalHistoryServiceProvider.getIfAvailable();
        if (signalHistoryService != null) {
            try {
                result.addAll(signalHistoryService.getRecentSignals(50));
            } catch (Exception ignored) {
                // Падающая БД не должна ломать дашборд, ниже сработает fallback.
            }
        }

        result.addAll(signalPort.buildFeed(pump, ema));

        if (ema.isEnabled()) {
            EmaStreamSignalService emaStreamSignalService = emaStreamSignalServiceProvider.getIfAvailable();
            if (emaStreamSignalService != null) {
                result.addAll(emaStreamSignalService.getRecentSignals(20));
            }
        }

        // Убираем дубли, если одинаковый сигнал пришел и из REST-пересчета, и из stream-потока.
        Map<String, Signal> deduplicated = result.stream()
                .sorted(Comparator.comparing(Signal::time).reversed())
                .collect(java.util.stream.Collectors.toMap(
                        this::dedupKey,
                        signal -> signal,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));

        return deduplicated.values().stream()
                .sorted(Comparator.comparing(Signal::time).reversed())
                .limit(50)
                .toList();
    }

    private String dedupKey(Signal signal) {
        return String.join("|",
                String.valueOf(signal.time()),
                String.valueOf(signal.symbol()),
                String.valueOf(signal.type()),
                String.valueOf(signal.text())
        );
    }
}
