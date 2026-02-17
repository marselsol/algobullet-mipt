package com.algobullet_mipt.controller;

import com.algobullet_mipt.portfolio.PortfolioAnalysis;
import com.algobullet_mipt.portfolio.PortfolioAnalysisService;
import com.algobullet_mipt.portfolio.PortfolioMetric;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(PortfolioController.class)
@AutoConfigureMockMvc
@WithMockUser(username = "smoke-user")
class PortfolioControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioAnalysisService analysisService;

    @Test
    void portfolioReturnsExpectedViewAndModel() throws Exception {
        PortfolioAnalysis analysis = new PortfolioAnalysis();
        analysis.setGeneratedAt(Instant.now());
        analysis.setMetrics(List.of(
                new PortfolioMetric("SHARPE", "Sharpe Ratio", "1.0", true, "ok")
        ));
        when(analysisService.getStubAnalysis()).thenReturn(analysis);

        mockMvc.perform(get("/portfolio"))
                .andExpect(status().isOk())
                .andExpect(view().name("portfolio"))
                .andExpect(model().attributeExists("title"))
                .andExpect(model().attributeExists("bybitConnected"))
                .andExpect(model().attribute("analysis", analysis));
    }

    @Test
    void connectSetsSessionFlagAndRedirects() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/portfolio/connect")
                        .with(csrf())
                        .session(session)
                        .param("apiKey", "test-key")
                        .param("apiSecret", "test-secret"))
                .andExpect(status().is3xxRedirection())
                .andExpect(request().sessionAttribute("bybitConnected", true))
                .andExpect(redirectedUrl("/portfolio"));
    }
}
