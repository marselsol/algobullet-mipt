package com.algobullet_mipt.service;

import com.algobullet_mipt.domain.signal.port.SignalPort;
import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.model.Signal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SignalFeedService {

    private final SignalPort signalPort;

    public List<Signal> buildFeed(PumpSettings pump, EmaSettings ema) {
        return signalPort.buildFeed(pump, ema);
    }
}
