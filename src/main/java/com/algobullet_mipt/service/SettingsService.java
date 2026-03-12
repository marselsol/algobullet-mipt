package com.algobullet_mipt.service;

import com.algobullet_mipt.entity.UserAccount;
import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SettingsService {
    private static final String ITEM_SEPARATOR = ",";
    private static final String EMA_WATCH_SEPARATOR = ";";
    private static final String EMA_FIELD_SEPARATOR = "\\|";
    private static final String EMA_FIELD_JOINER = "|";

    private final UserAccountService userAccountService;
    private final UserRepository userRepository;

    public SettingsService(
            UserAccountService userAccountService,
            UserRepository userRepository
    ) {
        this.userAccountService = userAccountService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PumpSettings pump() {
        return userAccountService.getCurrentUser()
                .map(this::toPumpSettings)
                .orElseGet(this::buildEmptyPumpSettings);
    }

    @Transactional(readOnly = true)
    public EmaSettings ema() {
        return userAccountService.getCurrentUser()
                .map(this::toEmaSettings)
                .orElseGet(this::buildEmptyEmaSettings);
    }

    @Transactional(readOnly = true)
    public List<OwnedPumpSettings> getAllPumpSettings() {
        return userRepository.findAll().stream()
                .map(user -> new OwnedPumpSettings(user.getId(), toPumpSettings(user)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OwnedEmaSettings> getAllEmaSettings() {
        return userRepository.findAll().stream()
                .map(user -> new OwnedEmaSettings(user.getId(), toEmaSettings(user)))
                .toList();
    }

    @Transactional
    public void savePumpSettings(PumpSettings form) {
        userAccountService.getCurrentUser().ifPresent(user -> {
            user.setPumpEnabled(form.isEnabled());
            user.setPumpMinChangePercent(form.getMinChangePercent());
            user.setPumpTimeframe(form.getTimeframe());
        });
    }

    @Transactional
    public boolean addPumpSymbol(String symbol) {
        Optional<UserAccount> owner = userAccountService.getCurrentUser();
        if (owner.isEmpty()) {
            return false;
        }

        PumpSettings settings = toPumpSettings(owner.get());
        boolean added = settings.addToWatchlist(symbol);
        if (added) {
            owner.get().setPumpWatchlist(serializePumpWatchlist(settings.getWatchlist()));
        }
        return added;
    }

    @Transactional
    public boolean removePumpSymbol(String symbol) {
        Optional<UserAccount> owner = userAccountService.getCurrentUser();
        if (owner.isEmpty()) {
            return false;
        }

        PumpSettings settings = toPumpSettings(owner.get());
        boolean removed = settings.removeFromWatchlist(symbol);
        if (removed) {
            owner.get().setPumpWatchlist(serializePumpWatchlist(settings.getWatchlist()));
        }
        return removed;
    }

    @Transactional
    public void saveEmaSettings(EmaSettings form) {
        userAccountService.getCurrentUser().ifPresent(user -> {
            user.setEmaEnabled(form.isEnabled());
            user.setEmaFast(form.getFast());
            user.setEmaSlow(form.getSlow());
            user.setEmaTimeframe(form.getTimeframe());
        });
    }

    @Transactional
    public boolean addEmaWatch(String symbol, int fast, int slow, String timeframe) {
        Optional<UserAccount> owner = userAccountService.getCurrentUser();
        if (owner.isEmpty()) {
            return false;
        }

        EmaSettings settings = toEmaSettings(owner.get());
        boolean added = settings.addToWatchlist(symbol, fast, slow, timeframe);
        if (added) {
            owner.get().setEmaWatchlist(serializeEmaWatchlist(settings.getWatchlist()));
        }
        return added;
    }

    @Transactional
    public boolean removeEmaWatch(String symbol, int fast, int slow, String timeframe) {
        Optional<UserAccount> owner = userAccountService.getCurrentUser();
        if (owner.isEmpty()) {
            return false;
        }

        EmaSettings settings = toEmaSettings(owner.get());
        boolean removed = settings.removeFromWatchlist(symbol, fast, slow, timeframe);
        if (removed) {
            owner.get().setEmaWatchlist(serializeEmaWatchlist(settings.getWatchlist()));
        }
        return removed;
    }

    private PumpSettings buildEmptyPumpSettings() {
        PumpSettings settings = new PumpSettings();
        clearPumpWatchlist(settings);
        return settings;
    }

    private EmaSettings buildEmptyEmaSettings() {
        EmaSettings settings = new EmaSettings();
        settings.clearWatchlist();
        return settings;
    }

    private PumpSettings toPumpSettings(UserAccount user) {
        PumpSettings settings = buildEmptyPumpSettings();
        settings.setEnabled(user.isPumpEnabled());
        settings.setMinChangePercent(user.getPumpMinChangePercent());
        settings.setTimeframe(user.getPumpTimeframe());

        List<String> watchlist = parsePumpWatchlist(user.getPumpWatchlist());
        for (String symbol : watchlist) {
            settings.addToWatchlist(symbol);
        }
        return settings;
    }

    private EmaSettings toEmaSettings(UserAccount user) {
        EmaSettings settings = buildEmptyEmaSettings();
        settings.setEnabled(user.isEmaEnabled());
        settings.setFast(user.getEmaFast());
        settings.setSlow(user.getEmaSlow());
        settings.setTimeframe(user.getEmaTimeframe());

        List<String> rawItems = parseSeparated(user.getEmaWatchlist(), EMA_WATCH_SEPARATOR);
        for (String item : rawItems) {
            String[] parts = item.split(EMA_FIELD_SEPARATOR);
            if (parts.length != 4) {
                continue;
            }
            try {
                settings.addToWatchlist(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), parts[3]);
            } catch (NumberFormatException ignored) {
                // Пропускаем битую запись настроек.
            }
        }

        return settings;
    }

    private String serializePumpWatchlist(List<String> watchlist) {
        return watchlist.isEmpty() ? "" : String.join(ITEM_SEPARATOR, watchlist);
    }

    private String serializeEmaWatchlist(List<EmaSettings.EmaWatch> watchlist) {
        if (watchlist.isEmpty()) {
            return "";
        }
        return watchlist.stream()
                .map(watch -> String.join(
                        EMA_FIELD_JOINER,
                        watch.getSymbol(),
                        String.valueOf(watch.getFast()),
                        String.valueOf(watch.getSlow()),
                        watch.getTimeframe()
                ))
                .collect(java.util.stream.Collectors.joining(EMA_WATCH_SEPARATOR));
    }

    private List<String> parsePumpWatchlist(String value) {
        return parseSeparated(value, ITEM_SEPARATOR);
    }

    private List<String> parseSeparated(String value, String separator) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(value.split(java.util.regex.Pattern.quote(separator)))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private void clearPumpWatchlist(PumpSettings settings) {
        for (String symbol : List.copyOf(settings.getWatchlist())) {
            settings.removeFromWatchlist(symbol);
        }
    }

    public record OwnedPumpSettings(Long userId, PumpSettings settings) {
    }

    public record OwnedEmaSettings(Long userId, EmaSettings settings) {
    }
}
