package com.algobullet_mipt.web;

import com.algobullet_mipt.domain.EmaSettings;
import com.algobullet_mipt.domain.PumpSettings;
import com.algobullet_mipt.domain.service.SettingsService;
import com.algobullet_mipt.domain.service.SignalFeedService;
import com.algobullet_mipt.web.dto.RegistrationForm;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller

public class PageController {

    private final SettingsService settings;
    private final SignalFeedService feed;

    public PageController(SettingsService settings, SignalFeedService feed) {
        this.settings = settings;
        this.feed = feed;
    }

    /** ЛЕНДИНГ: две кнопки — вход и регистрация */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("title", "ALGO BULLET");
        return "index"; // это теперь лендинг, НЕ лента сигналов
    }

    /** СТРАНИЦА ЛОГИНА (форма) */
    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("title", "Вход — ALGO BULLET");
        return "login";
    }

    /** СТРАНИЦА РЕГИСТРАЦИИ */
    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("title", "Регистрация — ALGO BULLET");
        model.addAttribute("form", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String handleRegister(@Valid @ModelAttribute("form") RegistrationForm form,
                                 BindingResult br, Model model) {
        if (br.hasErrors()) {
            model.addAttribute("title", "Регистрация — ALGO BULLET");
            return "register";
        }
//        log.info("New user: {}", form);
        System.out.printf("New user: {}", form);
        model.addAttribute("title", "Готово — ALGO BULLET");
        model.addAttribute("username", form.getUsername());
        return "success";
    }

    /** РАБОЧАЯ ОБЛАСТЬ (Дашборд) — личная лента сигналов пользователя */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("title", "Дашборд — ALGO BULLET");
        model.addAttribute("pump", settings.pump());
        model.addAttribute("ema", settings.ema());
        model.addAttribute("feed", feed.buildFeed(settings.pump(), settings.ema()));
        return "dashboard";
    }

    /** ПРОФИЛЬ (опционально — инфо о пользователе/кнопки) */
    @GetMapping("/profile")
    public String profile(Model model) {
        model.addAttribute("title", "Профиль — ALGO BULLET");
        model.addAttribute("pump", settings.pump());
        model.addAttribute("ema", settings.ema());
        return "profile";
    }

    /** Настройки Памп-скринера */
    @GetMapping("/settings/pump")
    public String pump(Model model) {
        model.addAttribute("title", "Памп-скринер — настройки");
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

    /** Настройки EMA */
    @GetMapping("/settings/ema")
    public String ema(Model model) {
        model.addAttribute("title", "EMA-пересечения — настройки");
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
