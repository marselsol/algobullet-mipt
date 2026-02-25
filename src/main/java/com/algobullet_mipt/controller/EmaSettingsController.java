package com.algobullet_mipt.controller;

import com.algobullet_mipt.domain.market.port.MarketDataPort;
import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.service.SettingsService;
import com.algobullet_mipt.service.ema.EmaStreamSignalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/settings/ema")
public class EmaSettingsController {

    private final SettingsService settings;
    private final MarketDataPort marketDataPort;
    private final ObjectProvider<EmaStreamSignalService> emaStreamSignalServiceProvider;

    @GetMapping
    public String view(Model model) {
        model.addAttribute("title", "EMA settings");
        model.addAttribute("ema", settings.ema());
        return "settings-ema";
    }

    @PostMapping
    public String update(@ModelAttribute("ema") EmaSettings form) {
        EmaSettings current = settings.ema();
        current.setEnabled(form.isEnabled());
        current.setFast(form.getFast());
        current.setSlow(form.getSlow());
        current.setTimeframe(form.getTimeframe());
        refreshEmaStreamSubscriptions();
        return "redirect:/settings/ema?ok";
    }

    @PostMapping("/watchlist/add")
    public String addSymbol(@RequestParam("symbol") String symbol,
                            @RequestParam("fast") int fast,
                            @RequestParam("slow") int slow,
                            @RequestParam("timeframe") String timeframe,
                            RedirectAttributes redirectAttributes) {
        var normalized = marketDataPort.normalizeLinearSymbol(symbol);
        if (normalized.isEmpty()) {
            redirectAttributes.addAttribute("watchlistInvalidSymbol", "true");
            return "redirect:/settings/ema";
        }

        boolean added = settings.ema().addToWatchlist(normalized.get(), fast, slow, timeframe);
        if (added) {
            refreshEmaStreamSubscriptions();
            redirectAttributes.addAttribute("watchlistAdded", "true");
        } else {
            redirectAttributes.addAttribute("watchlistError", "true");
        }
        return "redirect:/settings/ema";
    }

    @PostMapping("/watchlist/remove")
    public String removeSymbol(@RequestParam("symbol") String symbol,
                               RedirectAttributes redirectAttributes) {
        boolean removed = settings.ema().removeFromWatchlist(symbol);
        if (removed) {
            refreshEmaStreamSubscriptions();
            redirectAttributes.addAttribute("watchlistRemoved", "true");
        } else {
            redirectAttributes.addAttribute("watchlistError", "true");
        }
        return "redirect:/settings/ema";
    }

    private void refreshEmaStreamSubscriptions() {
        EmaStreamSignalService service = emaStreamSignalServiceProvider.getIfAvailable();
        if (service != null) {
            service.refreshSubscriptions();
        }
    }
}
