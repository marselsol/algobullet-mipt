package com.algobullet_mipt.service;

import com.algobullet_mipt.entity.SignalHistoryEntry;
import com.algobullet_mipt.entity.UserAccount;
import com.algobullet_mipt.model.Signal;
import com.algobullet_mipt.repository.SignalHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignalHistoryService {

    public static final String SOURCE_EMA_STREAM = "EMA_STREAM";
    public static final String SOURCE_PUMP_REST = "PUMP_REST";
    public static final String SOURCE_PUMP_WS = "PUMP_WS";

    private final SignalHistoryRepository repository;
    private final UserAccountService userAccountService;

    @Transactional(readOnly = true)
    public List<Signal> getRecentSignals(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        UserAccount user = userAccountService.getCurrentUser().orElse(null);
        if (user == null || user.getId() == null) {
            return List.of();
        }
        return repository.findByUserIdOrderBySignalTimeDescIdDesc(user.getId(), PageRequest.of(0, limit)).stream()
                .map(this::toSignal)
                .toList();
    }

    @Transactional
    public void saveEmaStreamSignal(Signal signal, String timeframe) {
        userAccountService.getCurrentUser()
                .map(UserAccount::getId)
                .ifPresent(userId -> saveSignal(userId, signal, timeframe, SOURCE_EMA_STREAM));
    }

    @Transactional
    public void saveEmaStreamSignal(Long userId, Signal signal, String timeframe) {
        saveSignal(userId, signal, timeframe, SOURCE_EMA_STREAM);
    }

    @Transactional
    public void savePumpRestSignal(Signal signal, String timeframe) {
        userAccountService.getCurrentUser()
                .map(UserAccount::getId)
                .ifPresent(userId -> saveSignal(userId, signal, timeframe, SOURCE_PUMP_REST));
    }

    @Transactional
    public void savePumpRestSignal(Long userId, Signal signal, String timeframe) {
        saveSignal(userId, signal, timeframe, SOURCE_PUMP_REST);
    }

    @Transactional
    public void savePumpWsSignal(Signal signal, String timeframe) {
        userAccountService.getCurrentUser()
                .map(UserAccount::getId)
                .ifPresent(userId -> saveSignal(userId, signal, timeframe, SOURCE_PUMP_WS));
    }

    @Transactional
    public void savePumpWsSignal(Long userId, Signal signal, String timeframe) {
        saveSignal(userId, signal, timeframe, SOURCE_PUMP_WS);
    }

    private void saveSignal(Long userId, Signal signal, String timeframe, String source) {
        if (userId == null || signal == null || signal.time() == null || signal.symbol() == null || signal.type() == null) {
            return;
        }

        String normalizedTimeframe = normalizeNullable(timeframe);
        if (repository.existsByUserIdAndSourceAndSymbolAndTimeframeAndTypeAndSignalTime(
                userId,
                source,
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
        entry.setSource(source);
        entry.setText(trimToLength(signal.text(), 512));
        entry.setStrength(signal.strength());
        UserAccount user = new UserAccount();
        user.setId(userId);
        entry.setUser(user);

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
