package com.algobullet_mipt.infrastructure.mock;

import com.algobullet_mipt.domain.signal.port.SignalPort;
import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.model.Signal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "app.features",
        name = "use-real-market-data",
        havingValue = "false",
        matchIfMissing = true
)
public class MockSignalPort implements SignalPort {

    @Override
    public List<Signal> buildFeed(PumpSettings pump, EmaSettings ema) {
        List<Signal> list = new ArrayList<>();
        Instant now = Instant.now();

        if (pump.isEnabled()) {
            list.add(new Signal(now.minusSeconds(60), "BTCUSDT", "PUMP",
                    "Fast move +%.1f%% on %s".formatted(pump.getMinChangePercent(), pump.getTimeframe()), 5));
            list.add(new Signal(now.minusSeconds(600), "SOLUSDT", "PUMP",
                    "Moderate move +%.1f%% on %s".formatted(pump.getMinChangePercent() * 0.8, pump.getTimeframe()), 3));
        }
        if (ema.isEnabled()) {
            list.add(new Signal(now.minusSeconds(180), "ETHUSDT", "EMA",
                    "EMA%s/%s crossover on %s".formatted(ema.getFast(), ema.getSlow(), ema.getTimeframe()), 4));
            list.add(new Signal(now.minusSeconds(900), "ARBUSDT", "EMA",
                    "EMA%s/%s trend confirmation on %s".formatted(ema.getFast(), ema.getSlow(), ema.getTimeframe()), 2));
        }

        list.sort(Comparator.comparing(Signal::time).reversed());
        return list;
    }
}
