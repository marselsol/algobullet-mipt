package com.algobullet_mipt.domain.service;

import com.algobullet_mipt.domain.EmaSettings;
import com.algobullet_mipt.domain.PumpSettings;
import com.algobullet_mipt.domain.Signal;
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
                    "Сильный рост +%.1f%% за %s".formatted(pump.getMinChangePercent(), pump.getTimeframe()), 5));
            list.add(new Signal(now.minusSeconds(600), "SOLUSDT", "PUMP",
                    "Импульс +%.1f%% за %s".formatted(pump.getMinChangePercent() * 0.8, pump.getTimeframe()), 3));
        }
        if (ema.isEnabled()) {
            list.add(new Signal(now.minusSeconds(180), "ETHUSDT", "EMA",
                    "Пересечение EMA%s/%s на %s".formatted(ema.getFast(), ema.getSlow(), ema.getTimeframe()), 4));
            list.add(new Signal(now.minusSeconds(900), "ARBUSDT", "EMA",
                    "Пересечение EMA%s/%s на %s (медвежье)".formatted(ema.getFast(), ema.getSlow(), ema.getTimeframe()), 2));
        }

        list.sort(Comparator.comparing(Signal::time).reversed());
        return list;
    }
}
