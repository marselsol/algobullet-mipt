package com.algobullet_mipt.service;

import com.algobullet_mipt.entity.UserAccount;
import com.algobullet_mipt.entity.UserEmaSettingsEntry;
import com.algobullet_mipt.entity.UserEmaWatchlistEntry;
import com.algobullet_mipt.entity.UserPumpSettingsEntry;
import com.algobullet_mipt.entity.UserPumpWatchlistEntry;
import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.repository.UserEmaSettingsRepository;
import com.algobullet_mipt.repository.UserEmaWatchlistRepository;
import com.algobullet_mipt.repository.UserPumpSettingsRepository;
import com.algobullet_mipt.repository.UserPumpWatchlistRepository;
import com.algobullet_mipt.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SettingsService {
    private final UserAccountService userAccountService;
    private final UserRepository userRepository;
    private final UserPumpSettingsRepository userPumpSettingsRepository;
    private final UserPumpWatchlistRepository userPumpWatchlistRepository;
    private final UserEmaSettingsRepository userEmaSettingsRepository;
    private final UserEmaWatchlistRepository userEmaWatchlistRepository;

    public SettingsService(
            UserAccountService userAccountService,
            UserRepository userRepository,
            UserPumpSettingsRepository userPumpSettingsRepository,
            UserPumpWatchlistRepository userPumpWatchlistRepository,
            UserEmaSettingsRepository userEmaSettingsRepository,
            UserEmaWatchlistRepository userEmaWatchlistRepository
    ) {
        this.userAccountService = userAccountService;
        this.userRepository = userRepository;
        this.userPumpSettingsRepository = userPumpSettingsRepository;
        this.userPumpWatchlistRepository = userPumpWatchlistRepository;
        this.userEmaSettingsRepository = userEmaSettingsRepository;
        this.userEmaWatchlistRepository = userEmaWatchlistRepository;
    }

    @Transactional(readOnly = true)
    public PumpSettings pump() {
        return userAccountService.getCurrentUser()
                .map(this::loadPumpSettings)
                .orElseGet(this::buildEmptyPumpSettings);
    }

    @Transactional(readOnly = true)
    public EmaSettings ema() {
        return userAccountService.getCurrentUser()
                .map(this::loadEmaSettings)
                .orElseGet(this::buildEmptyEmaSettings);
    }

    @Transactional(readOnly = true)
    public List<OwnedPumpSettings> getAllPumpSettings() {
        return userRepository.findAll().stream()
                .map(user -> new OwnedPumpSettings(user.getId(), loadPumpSettings(user)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OwnedEmaSettings> getAllEmaSettings() {
        return userRepository.findAll().stream()
                .map(user -> new OwnedEmaSettings(user.getId(), loadEmaSettings(user)))
                .toList();
    }

    @Transactional
    public void savePumpSettings(PumpSettings form) {
        userAccountService.getCurrentUser().ifPresent(user -> {
            UserPumpSettingsEntry entry = userPumpSettingsRepository.findById(user.getId())
                    .orElseGet(() -> createPumpSettingsEntry(user.getId()));
            entry.setEnabled(form.isEnabled());
            entry.setMinChangePercent(form.getMinChangePercent());
            entry.setTimeframe(form.getTimeframe());
            userPumpSettingsRepository.save(entry);
        });
    }

    @Transactional
    public boolean addPumpSymbol(String symbol) {
        Optional<UserAccount> owner = userAccountService.getCurrentUser();
        if (owner.isEmpty()) {
            return false;
        }

        PumpSettings settings = loadPumpSettings(owner.get());
        boolean added = settings.addToWatchlist(symbol);
        if (added) {
            savePumpWatchlist(owner.get().getId(), settings.getWatchlist());
        }
        return added;
    }

    @Transactional
    public boolean removePumpSymbol(String symbol) {
        Optional<UserAccount> owner = userAccountService.getCurrentUser();
        if (owner.isEmpty()) {
            return false;
        }

        PumpSettings settings = loadPumpSettings(owner.get());
        boolean removed = settings.removeFromWatchlist(symbol);
        if (removed) {
            savePumpWatchlist(owner.get().getId(), settings.getWatchlist());
        }
        return removed;
    }

    @Transactional
    public void saveEmaSettings(EmaSettings form) {
        userAccountService.getCurrentUser().ifPresent(user -> {
            UserEmaSettingsEntry entry = userEmaSettingsRepository.findById(user.getId())
                    .orElseGet(() -> createEmaSettingsEntry(user.getId()));
            entry.setEnabled(form.isEnabled());
            entry.setFast(form.getFast());
            entry.setSlow(form.getSlow());
            entry.setTimeframe(form.getTimeframe());
            userEmaSettingsRepository.save(entry);
        });
    }

    @Transactional
    public boolean addEmaWatch(String symbol, int fast, int slow, String timeframe) {
        Optional<UserAccount> owner = userAccountService.getCurrentUser();
        if (owner.isEmpty()) {
            return false;
        }

        EmaSettings settings = loadEmaSettings(owner.get());
        boolean added = settings.addToWatchlist(symbol, fast, slow, timeframe);
        if (added) {
            saveEmaWatchlist(owner.get().getId(), settings.getWatchlist());
        }
        return added;
    }

    @Transactional
    public boolean removeEmaWatch(String symbol, int fast, int slow, String timeframe) {
        Optional<UserAccount> owner = userAccountService.getCurrentUser();
        if (owner.isEmpty()) {
            return false;
        }

        EmaSettings settings = loadEmaSettings(owner.get());
        boolean removed = settings.removeFromWatchlist(symbol, fast, slow, timeframe);
        if (removed) {
            saveEmaWatchlist(owner.get().getId(), settings.getWatchlist());
        }
        return removed;
    }

    private PumpSettings loadPumpSettings(UserAccount user) {
        UserPumpSettingsEntry settingsEntry = userPumpSettingsRepository.findById(user.getId())
                .orElseGet(() -> createPumpSettingsEntry(user.getId()));
        List<UserPumpWatchlistEntry> watchlistEntries = userPumpWatchlistRepository.findByUserIdOrderByIdAsc(user.getId());

        PumpSettings settings = buildEmptyPumpSettings();
        settings.setEnabled(settingsEntry.isEnabled());
        settings.setMinChangePercent(settingsEntry.getMinChangePercent());
        settings.setTimeframe(settingsEntry.getTimeframe());

        for (UserPumpWatchlistEntry watch : watchlistEntries) {
            settings.addToWatchlist(watch.getSymbol());
        }
        return settings;
    }

    private EmaSettings loadEmaSettings(UserAccount user) {
        UserEmaSettingsEntry settingsEntry = userEmaSettingsRepository.findById(user.getId())
                .orElseGet(() -> createEmaSettingsEntry(user.getId()));
        List<UserEmaWatchlistEntry> watchlistEntries = userEmaWatchlistRepository.findByUserIdOrderByIdAsc(user.getId());

        EmaSettings settings = buildEmptyEmaSettings();
        settings.setEnabled(settingsEntry.isEnabled());
        settings.setFast(settingsEntry.getFast());
        settings.setSlow(settingsEntry.getSlow());
        settings.setTimeframe(settingsEntry.getTimeframe());

        for (UserEmaWatchlistEntry watch : watchlistEntries) {
            settings.addToWatchlist(watch.getSymbol(), watch.getFast(), watch.getSlow(), watch.getTimeframe());
        }
        return settings;
    }

    private void savePumpWatchlist(Long userId, List<String> watchlist) {
        userPumpWatchlistRepository.deleteByUserId(userId);
        userPumpWatchlistRepository.flush();
        for (String symbol : watchlist) {
            UserPumpWatchlistEntry entry = new UserPumpWatchlistEntry();
            entry.setUserId(userId);
            entry.setSymbol(symbol);
            userPumpWatchlistRepository.save(entry);
        }
    }

    private void saveEmaWatchlist(Long userId, List<EmaSettings.EmaWatch> watchlist) {
        userEmaWatchlistRepository.deleteByUserId(userId);
        userEmaWatchlistRepository.flush();
        for (EmaSettings.EmaWatch watch : watchlist) {
            UserEmaWatchlistEntry entry = new UserEmaWatchlistEntry();
            entry.setUserId(userId);
            entry.setSymbol(watch.getSymbol());
            entry.setFast(watch.getFast());
            entry.setSlow(watch.getSlow());
            entry.setTimeframe(watch.getTimeframe());
            userEmaWatchlistRepository.save(entry);
        }
    }

    private UserPumpSettingsEntry createPumpSettingsEntry(Long userId) {
        UserPumpSettingsEntry entry = new UserPumpSettingsEntry();
        entry.setUserId(userId);
        return entry;
    }

    private UserEmaSettingsEntry createEmaSettingsEntry(Long userId) {
        UserEmaSettingsEntry entry = new UserEmaSettingsEntry();
        entry.setUserId(userId);
        return entry;
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
