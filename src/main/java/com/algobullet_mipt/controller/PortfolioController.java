package com.algobullet_mipt.controller;

import com.algobullet_mipt.entity.UserAccount;
import com.algobullet_mipt.portfolio.PortfolioAnalysis;
import com.algobullet_mipt.portfolio.PortfolioAnalysisService;
import com.algobullet_mipt.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/portfolio")
public class PortfolioController {

    private final PortfolioAnalysisService analysisService;
    private final UserAccountService userAccountService;

    @GetMapping
    public String portfolio(Model model) {
        model.addAttribute("title", "Анализ портфеля - ALGOBULLET");

        UserAccount user = userAccountService.getCurrentUser().orElse(null);
        boolean connected = user != null
                && user.getBybitApiKey() != null && !user.getBybitApiKey().isBlank()
                && user.getBybitApiSecret() != null && !user.getBybitApiSecret().isBlank();

        model.addAttribute("bybitConnected", connected);

        PortfolioAnalysis analysis = analysisService.getStubAnalysis();
        model.addAttribute("analysis", analysis);

        return "portfolio";
    }
}
