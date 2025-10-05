package com.algobullet_mipt.domain.service;


import com.algobullet_mipt.domain.EmaSettings;
import com.algobullet_mipt.domain.PumpSettings;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {
    private final PumpSettings pump = new PumpSettings();
    private final EmaSettings ema = new EmaSettings();

    public PumpSettings pump() {
        return pump;
    }

    public EmaSettings ema() {
        return ema;
    }
}
