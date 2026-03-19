package com.algobullet_mipt.controller;

import com.algobullet_mipt.experiment.bybitlatency.BybitLatencyResultsService;
import com.algobullet_mipt.experiment.bybitlatency.WsLatencyExperimentService;
import com.algobullet_mipt.service.AppFeatureFlagService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class BybitLatencyExperimentController {

    private final BybitLatencyResultsService resultsService;
    private final AppFeatureFlagService featureFlagService;
    private final ObjectProvider<WsLatencyExperimentService> wsLatencyExperimentServiceProvider;

    @GetMapping("/experiments/bybit-latency")
    public String page(
            @RequestParam(value = "runId", required = false) String runId,
            Model model
    ) {
        BybitLatencyResultsService.ResultsView results = resultsService.getResults(runId);
        model.addAttribute("title", "Bybit latency experiment - ALGOBULLET");
        model.addAttribute("latencyExperimentEnabled", featureFlagService.isBybitLatencyExperimentEnabled());
        model.addAttribute("results", results);
        return "experiment-bybit-latency-ru";
    }

    @PostMapping("/experiments/bybit-latency/toggle")
    public String toggle(
            @RequestParam("enabled") boolean enabled,
            RedirectAttributes redirectAttributes
    ) {
        featureFlagService.setBybitLatencyExperimentEnabled(enabled);
        WsLatencyExperimentService wsService = wsLatencyExperimentServiceProvider.getIfAvailable();
        if (wsService != null) {
            wsService.refreshState();
        }
        redirectAttributes.addAttribute("stateChanged", "true");
        return "redirect:/experiments/bybit-latency";
    }

    @GetMapping("/api/experiments/bybit-latency")
    @ResponseBody
    public ResponseEntity<BybitLatencyResultsService.ResultsView> api(
            @RequestParam(value = "runId", required = false) String runId
    ) {
        return ResponseEntity.ok(resultsService.getResults(runId));
    }
}
