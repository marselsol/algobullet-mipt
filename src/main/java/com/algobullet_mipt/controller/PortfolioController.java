package com.algobullet_mipt.controller;

import com.algobullet_mipt.portfolio.PortfolioAnalysis;
import com.algobullet_mipt.portfolio.PortfolioAnalysisService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@RequestMapping("/portfolio")
public class PortfolioController {

    private final PortfolioAnalysisService analysisService;

    @GetMapping
    public String portfolio(Model model, HttpSession session) {
        model.addAttribute("title", "Анализ портфеля - ALGOBULLET");

        boolean connected = Boolean.TRUE.equals(session.getAttribute("bybitConnected"));
        model.addAttribute("bybitConnected", connected);

        PortfolioAnalysis analysis = analysisService.getStubAnalysis();
        model.addAttribute("analysis", analysis);

        return "portfolio";
    }

    @PostMapping("/connect")
    public String connectBybit(@RequestParam("apiKey") String apiKey,
                               @RequestParam("apiSecret") String apiSecret,
                               HttpSession session) {
        // Заглушка: сохраняем флаг подключения в сессии, ключи не храним и никуда не отправляем
        session.setAttribute("bybitConnected", true);
        // Возврат на страницу анализа портфеля
        return "redirect:/portfolio";
    }
}

