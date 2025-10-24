package com.algobullet_mipt.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationForm {

    @NotBlank(message = "Укажите логин")
    @Size(min = 3, max = 32, message = "Логин должен быть от 3 до 32 символов")
    private String username;

    @NotBlank(message = "Укажите e-mail")
    @Email(message = "Некорректный e-mail")
    private String email;

    @NotBlank(message = "Придумайте пароль")
    @Size(min = 6, max = 64, message = "Пароль должен быть от 6 до 64 символов")
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
                "username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                '}';
    }
}
