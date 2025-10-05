package com.algobullet_mipt.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationForm {
    @NotBlank(message = "Укажите логин")
    @Size(min = 3, max = 32, message = "Логин 3–32 символа")
    private String username;

    @NotBlank(message = "Укажите почту")
    @Email(message = "Некорректная почта")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 6, max = 64, message = "Пароль 6–64 символа")
    private String password;

    @NotBlank(message = "Повторите пароль")
    private String confirmPassword;

    @NotBlank(message = "Укажите телефон")
    private String phone;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @Override
    public String toString() {
        return "RegistrationForm{" +
                "username='" + username +
                ", email='" + email +
                ", phone='" + phone +
                '}';
    }
}