package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.signal.port.SignalPort;
import com.algobullet_mipt.infrastructure.mock.MockSignalPort;
import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.model.Signal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "app.features",
        name = "use-real-market-data",
        havingValue = "true"
)
public class BybitSignalPort implements SignalPort {

    private final MockSignalPort fallback = new MockSignalPort();

    @Override
    public List<Signal> buildFeed(PumpSettings pump, EmaSettings ema) {
        // TODO: replace with Bybit/ta4j-backed signal generation.
        return fallback.buildFeed(pump, ema);
    }
}
