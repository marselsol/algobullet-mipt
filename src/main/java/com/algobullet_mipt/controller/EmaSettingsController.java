package com.algobullet_mipt.controller;

import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/settings/ema")
public class EmaSettingsController {

    private final SettingsService settings;

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
        return "redirect:/settings/ema?ok";
    }
}
