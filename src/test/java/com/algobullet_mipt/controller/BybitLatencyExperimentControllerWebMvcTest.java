package com.algobullet_mipt.controller;

import com.algobullet_mipt.experiment.bybitlatency.BybitLatencyResultsService;
import com.algobullet_mipt.experiment.bybitlatency.WsLatencyExperimentService;
import com.algobullet_mipt.security.CustomUserDetailsService;
import com.algobullet_mipt.security.SecurityConfig;
import com.algobullet_mipt.service.AppFeatureFlagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(BybitLatencyExperimentController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class BybitLatencyExperimentControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BybitLatencyResultsService resultsService;

    @MockBean
    private AppFeatureFlagService featureFlagService;

    @MockBean
    private WsLatencyExperimentService wsLatencyExperimentService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void pageIsAvailableForAdmin() throws Exception {
        BybitLatencyResultsService.ResultsView results = emptyResults();
        when(resultsService.getResults(null)).thenReturn(results);
        when(featureFlagService.isBybitLatencyExperimentEnabled()).thenReturn(true);

        mockMvc.perform(get("/experiments/bybit-latency"))
                .andExpect(status().isOk())
                .andExpect(view().name("experiment-bybit-latency-ru"))
                .andExpect(model().attribute("results", results))
                .andExpect(model().attribute("latencyExperimentEnabled", true));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void pageIsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/experiments/bybit-latency"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void toggleUpdatesFlagAndRefreshesWsRuntime() throws Exception {
        mockMvc.perform(post("/experiments/bybit-latency/toggle")
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/experiments/bybit-latency?stateChanged=true"));

        verify(featureFlagService).setBybitLatencyExperimentEnabled(false);
        verify(wsLatencyExperimentService).refreshState();
    }

    private BybitLatencyResultsService.ResultsView emptyResults() {
        return new BybitLatencyResultsService.ResultsView(
                "run-current",
                "run-current",
                List.of("run-current"),
                "DUAL",
                List.of("BTCUSDT"),
                "1m",
                200L,
                List.of(),
                List.of()
        );
    }
}
