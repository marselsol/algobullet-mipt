package com.algobullet_mipt.service;

import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.model.Signal;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SignalFeedService {

    public List<Signal> buildFeed(PumpSettings pump, EmaSettings ema) {
        List<Signal> list = new ArrayList<>();
        Instant now = Instant.now();

        if (pump.isEnabled()) {
            list.add(new Signal(now.minusSeconds(60), "BTCUSDT", "PUMP",
                    "Резкий рост +%.1f%% за %s".formatted(pump.getMinChangePercent(), pump.getTimeframe()), 5));
            list.add(new Signal(now.minusSeconds(600), "SOLUSDT", "PUMP",
                    "Умеренный рост +%.1f%% за %s".formatted(pump.getMinChangePercent() * 0.8, pump.getTimeframe()), 3));
        }
        if (ema.isEnabled()) {
            list.add(new Signal(now.minusSeconds(180), "ETHUSDT", "EMA",
                    "Пересечение EMA%s/%s на %s".formatted(ema.getFast(), ema.getSlow(), ema.getTimeframe()), 4));
            list.add(new Signal(now.minusSeconds(900), "ARBUSDT", "EMA",
                    "EMA%s/%s подтверждает тренд на %s".formatted(ema.getFast(), ema.getSlow(), ema.getTimeframe()), 2));
        }

        list.sort(Comparator.comparing(Signal::time).reversed());
        return list;
    }
}
