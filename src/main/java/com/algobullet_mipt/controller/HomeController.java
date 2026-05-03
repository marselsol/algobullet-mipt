package com.algobullet_mipt.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("title", "ALGOBULLET");
        return "index";
    }

    @GetMapping("/pricing")
    public String pricing(Model model) {
        model.addAttribute("title", "Тарифы | ALGOBULLET");
        return "pricing";
    }

    @GetMapping("/contacts")
    public String contacts(Model model) {
        model.addAttribute("title", "Контакты и реквизиты | ALGOBULLET");
        return "contacts";
    }

    @GetMapping("/legal")
    public String legal(Model model) {
        model.addAttribute("title", "Пользовательское соглашение | ALGOBULLET");
        return "legal";
    }
}
