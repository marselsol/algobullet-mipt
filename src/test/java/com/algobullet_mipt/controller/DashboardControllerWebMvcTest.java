package com.algobullet_mipt.controller;

import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.model.Signal;
import com.algobullet_mipt.service.SettingsService;
import com.algobullet_mipt.service.SignalFeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc
@WithMockUser(username = "smoke-user")
class DashboardControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettingsService settingsService;

    @MockBean
    private SignalFeedService signalFeedService;

    @Test
    void dashboardReturnsExpectedViewAndModel() throws Exception {
        PumpSettings pump = new PumpSettings();
        EmaSettings ema = new EmaSettings();
        List<Signal> feed = List.of(new Signal(Instant.now(), "BTCUSDT", "PUMP", "Smoke signal", 3));

        when(settingsService.pump()).thenReturn(pump);
        when(settingsService.ema()).thenReturn(ema);
        when(signalFeedService.buildFeed(pump, ema)).thenReturn(feed);

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("title"))
                .andExpect(model().attribute("pump", pump))
                .andExpect(model().attribute("ema", ema))
                .andExpect(model().attribute("feed", feed));
    }
}
