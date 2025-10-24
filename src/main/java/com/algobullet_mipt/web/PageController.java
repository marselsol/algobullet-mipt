package com.algobullet_mipt.web;

import com.algobullet_mipt.domain.EmaSettings;
import com.algobullet_mipt.domain.PumpSettings;
import com.algobullet_mipt.domain.service.SettingsService;
import com.algobullet_mipt.domain.service.SignalFeedService;
import com.algobullet_mipt.domain.service.UserAccountService;
import com.algobullet_mipt.domain.service.UserRegistrationException;
import com.algobullet_mipt.web.dto.RegistrationForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PageController {

    private final SettingsService settings;
    private final SignalFeedService feed;
    private final UserAccountService users;

    public PageController(SettingsService settings, SignalFeedService feed, UserAccountService users) {
        this.settings = settings;
        this.feed = feed;
        this.users = users;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("title", "ALGOBULLET");
        return "index";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("title", "Sign in - ALGOBULLET");
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("title", "Create account - ALGOBULLET");
        model.addAttribute("form", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String handleRegister(@Valid @ModelAttribute("form") RegistrationForm form,
                                 BindingResult br, Model model) {
        if (br.hasErrors()) {
            model.addAttribute("title", "Create account - ALGOBULLET");
            return "register";
        }
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            br.rejectValue("confirmPassword", "registration.password.mismatch", "Passwords do not match");
            model.addAttribute("title", "Create account - ALGOBULLET");
            return "register";
        }

        try {
            users.registerUser(form.getUsername(), form.getEmail(), form.getPhone(), form.getPassword());
        } catch (UserRegistrationException ex) {
            br.rejectValue(ex.getField(), "registration." + ex.getField(), ex.getMessage());
            model.addAttribute("title", "Create account - ALGOBULLET");
            return "register";
        }

        model.addAttribute("title", "Registration complete");
        model.addAttribute("username", form.getUsername());
        return "success";
    }

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

    @GetMapping("/settings/pump")
    public String pump(Model model) {
        model.addAttribute("title", "Pump settings");
        model.addAttribute("pump", settings.pump());
        return "settings-pump";
    }

    @PostMapping("/settings/pump")
    public String pumpUpdate(@ModelAttribute("pump") PumpSettings form) {
        PumpSettings s = settings.pump();
        s.setEnabled(form.isEnabled());
        s.setMinChangePercent(form.getMinChangePercent());
        s.setTimeframe(form.getTimeframe());
        return "redirect:/settings/pump?ok";
    }

    @PostMapping("/settings/pump/watchlist/add")
    public String pumpWatchlistAdd(@RequestParam("symbol") String symbol,
                                   RedirectAttributes redirectAttributes) {
        boolean added = settings.pump().addToWatchlist(symbol);
        if (added) {
            redirectAttributes.addAttribute("watchlistAdded", "true");
        } else {
            redirectAttributes.addAttribute("watchlistError", "true");
        }
        return "redirect:/settings/pump";
    }

    @PostMapping("/settings/pump/watchlist/remove")
    public String pumpWatchlistRemove(@RequestParam("symbol") String symbol,
                                      RedirectAttributes redirectAttributes) {
        boolean removed = settings.pump().removeFromWatchlist(symbol);
        if (removed) {
            redirectAttributes.addAttribute("watchlistRemoved", "true");
        } else {
            redirectAttributes.addAttribute("watchlistError", "true");
        }
        return "redirect:/settings/pump";
    }

    @GetMapping("/settings/ema")
    public String ema(Model model) {
        model.addAttribute("title", "EMA settings");
        model.addAttribute("ema", settings.ema());
        return "settings-ema";
    }

    @PostMapping("/settings/ema")
    public String emaUpdate(@ModelAttribute("ema") EmaSettings form) {
        EmaSettings s = settings.ema();
        s.setEnabled(form.isEnabled());
        s.setFast(form.getFast());
        s.setSlow(form.getSlow());
        s.setTimeframe(form.getTimeframe());
        return "redirect:/settings/ema?ok";
    }
}
