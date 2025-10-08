package com.algobullet_mipt.domain.service;

import com.algobullet_mipt.repository.UserRepository;
import com.algobullet_mipt.repository.entity.UserAccount;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount registerUser(String username, String email, String phone, String rawPassword) {
        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase();
        String normalizedPhone = phone == null ? null : phone.trim();

        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new UserRegistrationException("username", "Username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new UserRegistrationException("email", "Email is already registered");
        }

        UserAccount account = new UserAccount();
        account.setUsername(normalizedUsername);
        account.setEmail(normalizedEmail);
        account.setPhone(normalizedPhone);
        account.setPassword(passwordEncoder.encode(rawPassword));
        return userRepository.save(account);
    }
}
