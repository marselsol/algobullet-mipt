package com.algobullet_mipt.domain.signal.port;

import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.model.Signal;

import java.util.List;

public interface SignalPort {
    List<Signal> buildFeed(PumpSettings pump, EmaSettings ema);
}
