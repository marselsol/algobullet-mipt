package com.algobullet_mipt.service;

import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsServiceTest {

    @Test
    void modelDefaultsStayEmptyForNewUsers() {
        PumpSettings pumpSettings = new PumpSettings();
        EmaSettings emaSettings = new EmaSettings();
        emaSettings.clearWatchlist();

        assertThat(pumpSettings.getWatchlist()).isEmpty();
        assertThat(emaSettings.getWatchlist()).isEmpty();
    }
}
