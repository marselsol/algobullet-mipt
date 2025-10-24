package com.algobullet_mipt.controller;

import com.algobullet_mipt.service.SettingsService;
import com.algobullet_mipt.service.SignalFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final SettingsService settings;
    private final SignalFeedService feed;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("title", "Dashboard - ALGOBULLET");
        model.addAttribute("pump", settings.pump());
        model.addAttribute("ema", settings.ema());
        model.addAttribute("feed", feed.buildFeed(settings.pump(), settings.ema()));
        return "dashboard";
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        model.addAttribute("title", "Profile - ALGOBULLET");
        model.addAttribute("pump", settings.pump());
        model.addAttribute("ema", settings.ema());
        return "profile";
    }
}
