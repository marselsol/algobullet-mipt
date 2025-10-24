package com.algobullet_mipt.controller;

import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/settings/pump")
public class PumpSettingsController {

    private final SettingsService settings;

    @GetMapping
    public String view(Model model) {
        model.addAttribute("title", "Pump settings");
        model.addAttribute("pump", settings.pump());
        return "settings-pump";
    }

    @PostMapping
    public String update(@ModelAttribute("pump") PumpSettings form) {
        PumpSettings current = settings.pump();
        current.setEnabled(form.isEnabled());
        current.setMinChangePercent(form.getMinChangePercent());
        current.setTimeframe(form.getTimeframe());
        return "redirect:/settings/pump?ok";
    }

    @PostMapping("/watchlist/add")
    public String addSymbol(@RequestParam("symbol") String symbol,
                             RedirectAttributes redirectAttributes) {
        boolean added = settings.pump().addToWatchlist(symbol);
        if (added) {
            redirectAttributes.addAttribute("watchlistAdded", "true");
        } else {
            redirectAttributes.addAttribute("watchlistError", "true");
        }
        return "redirect:/settings/pump";
    }

    @PostMapping("/watchlist/remove")
    public String removeSymbol(@RequestParam("symbol") String symbol,
                                RedirectAttributes redirectAttributes) {
        boolean removed = settings.pump().removeFromWatchlist(symbol);
        if (removed) {
            redirectAttributes.addAttribute("watchlistRemoved", "true");
        } else {
            redirectAttributes.addAttribute("watchlistError", "true");
        }
        return "redirect:/settings/pump";
    }
}
