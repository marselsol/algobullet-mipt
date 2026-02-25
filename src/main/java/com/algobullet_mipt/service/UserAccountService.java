package com.algobullet_mipt.service;

import com.algobullet_mipt.entity.UserAccount;
import com.algobullet_mipt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

    @Transactional(readOnly = true)
    public Optional<UserAccount> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        String username = authentication.getName();
        if (username == null || username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) {
            return Optional.empty();
        }

        return userRepository.findByUsernameIgnoreCase(username.trim());
    }

    @Transactional
    public boolean updateCurrentUserBybitCredentials(String apiKey, String apiSecret, boolean clearCredentials) {
        Optional<UserAccount> currentUser = getCurrentUser();
        if (currentUser.isEmpty()) {
            return false;
        }

        UserAccount user = currentUser.get();
        if (clearCredentials) {
            user.setBybitApiKey(null);
            user.setBybitApiSecret(null);
            return true;
        }

        String normalizedKey = normalizeNullable(apiKey);
        String normalizedSecret = normalizeNullable(apiSecret);

        if (normalizedKey != null) {
            user.setBybitApiKey(normalizedKey);
        }

        // Пустой секрет в форме означает "не менять".
        if (normalizedSecret != null) {
            user.setBybitApiSecret(normalizedSecret);
        }

        // Если ключ очистили, секрет тоже очищаем, чтобы не держать полумертвую пару.
        if (user.getBybitApiKey() == null || user.getBybitApiKey().isBlank()) {
            user.setBybitApiKey(null);
            user.setBybitApiSecret(null);
        }

        return true;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
