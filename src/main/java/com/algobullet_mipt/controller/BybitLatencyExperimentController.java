package com.algobullet_mipt.controller;

import com.algobullet_mipt.experiment.bybitlatency.BybitLatencyResultsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class BybitLatencyExperimentController {

    private final BybitLatencyResultsService resultsService;

    @GetMapping("/experiments/bybit-latency")
    public String page(
            @RequestParam(value = "runId", required = false) String runId,
            Model model
    ) {
        BybitLatencyResultsService.ResultsView results = resultsService.getResults(runId);
        model.addAttribute("title", "Сравнение задержек Bybit - ALGOBULLET");
        model.addAttribute("results", results);
        return "experiment-bybit-latency-ru";
    }

    @GetMapping("/api/experiments/bybit-latency")
    @ResponseBody
    public ResponseEntity<BybitLatencyResultsService.ResultsView> api(
            @RequestParam(value = "runId", required = false) String runId
    ) {
        return ResponseEntity.ok(resultsService.getResults(runId));
    }
}
