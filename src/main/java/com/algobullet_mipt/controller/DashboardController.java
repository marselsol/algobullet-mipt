package com.algobullet_mipt.controller;

import com.algobullet_mipt.entity.UserAccount;
import com.algobullet_mipt.service.SettingsService;
import com.algobullet_mipt.service.SignalFeedService;
import com.algobullet_mipt.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final SettingsService settings;
    private final SignalFeedService feed;
    private final UserAccountService userAccountService;

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
        UserAccount user = userAccountService.getCurrentUser().orElse(null);
        model.addAttribute("bybitApiKey", user != null ? user.getBybitApiKey() : "");
        model.addAttribute("hasBybitApiSecret", user != null && user.getBybitApiSecret() != null && !user.getBybitApiSecret().isBlank());
        return "profile";
    }

    @PostMapping("/profile/bybit")
    public String updateBybitCredentials(@RequestParam(value = "apiKey", required = false) String apiKey,
                                         @RequestParam(value = "apiSecret", required = false) String apiSecret,
                                         @RequestParam(value = "clearCredentials", defaultValue = "false") boolean clearCredentials) {
        boolean updated = userAccountService.updateCurrentUserBybitCredentials(apiKey, apiSecret, clearCredentials);
        return updated ? "redirect:/profile?bybitSaved" : "redirect:/profile?bybitError";
    }
}
