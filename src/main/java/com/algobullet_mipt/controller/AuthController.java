package com.algobullet_mipt.controller;

import com.algobullet_mipt.dto.RegistrationForm;
import com.algobullet_mipt.service.UserAccountService;
import com.algobullet_mipt.service.UserRegistrationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserAccountService users;

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
}
