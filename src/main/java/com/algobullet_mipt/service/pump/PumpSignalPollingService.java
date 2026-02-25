package com.algobullet_mipt.service.pump;

import com.algobullet_mipt.domain.signal.port.SignalPort;
import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.model.Signal;
import com.algobullet_mipt.service.SettingsService;
import com.algobullet_mipt.service.SignalHistoryService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.features",
        name = "use-real-market-data",
        havingValue = "true"
)
@Slf4j
public class PumpSignalPollingService {

    private final SignalPort signalPort;
    private final SettingsService settingsService;
    private final SignalHistoryService signalHistoryService;

    @PostConstruct
    public void init() {
        pollOnce();
    }

    @Scheduled(fixedDelayString = "${app.pump.polling-delay-ms:15000}")
    public void pollOnce() {
        PumpSettings pump = settingsService.pump();
        if (!pump.isEnabled()) {
            return;
        }

        EmaSettings emaDisabled = new EmaSettings();
        emaDisabled.setEnabled(false);

        try {
            List<Signal> signals = signalPort.buildFeed(pump, emaDisabled);
            for (Signal signal : signals) {
                if (signal == null || signal.type() == null) {
                    continue;
                }
                if (!"PUMP".equalsIgnoreCase(signal.type())) {
                    continue;
                }
                signalHistoryService.savePumpRestSignal(signal, pump.getTimeframe());
            }
        } catch (Exception ex) {
            log.warn("Pump polling error: {}", ex.getMessage());
        }
    }
}
