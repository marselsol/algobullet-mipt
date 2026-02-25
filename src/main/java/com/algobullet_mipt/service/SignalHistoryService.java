package com.algobullet_mipt.service;

import com.algobullet_mipt.entity.SignalHistoryEntry;
import com.algobullet_mipt.model.Signal;
import com.algobullet_mipt.repository.SignalHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignalHistoryService {

    public static final String SOURCE_EMA_STREAM = "EMA_STREAM";

    private final SignalHistoryRepository repository;

    @Transactional(readOnly = true)
    public List<Signal> getRecentSignals(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return repository.findTop50ByOrderBySignalTimeDescIdDesc().stream()
                .limit(limit)
                .map(this::toSignal)
                .toList();
    }

    @Transactional
    public void saveEmaStreamSignal(Signal signal, String timeframe) {
        if (signal == null || signal.time() == null || signal.symbol() == null || signal.type() == null) {
            return;
        }

        String normalizedTimeframe = normalizeNullable(timeframe);
        if (repository.existsBySourceAndSymbolAndTimeframeAndTypeAndSignalTime(
                SOURCE_EMA_STREAM,
                signal.symbol(),
                normalizedTimeframe,
                signal.type(),
                signal.time()
        )) {
            return;
        }

        SignalHistoryEntry entry = new SignalHistoryEntry();
        entry.setSignalTime(signal.time());
        entry.setSymbol(signal.symbol());
        entry.setTimeframe(normalizedTimeframe);
        entry.setType(signal.type());
        entry.setSource(SOURCE_EMA_STREAM);
        entry.setText(trimToLength(signal.text(), 512));
        entry.setStrength(signal.strength());

        try {
            repository.save(entry);
        } catch (DataIntegrityViolationException ex) {
            // Защита от гонок при параллельной записи дублей.
            log.debug("Сигнал уже сохранен (дубль): {} {} {} {}", signal.symbol(), normalizedTimeframe, signal.type(), signal.time());
        }
    }

    private Signal toSignal(SignalHistoryEntry entry) {
        return new Signal(
                entry.getSignalTime(),
                entry.getSymbol(),
                entry.getType(),
                entry.getText(),
                entry.getStrength()
        );
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String trimToLength(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
