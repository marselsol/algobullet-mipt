package com.algobullet_mipt.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "app_users", schema = "algo")
@Data
public class UserAccount implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, unique = true, length = 128)
    private String email;

    @Column(nullable = false, length = 255)
    @ToString.Exclude
    private String password;

    @Column(length = 32)
    private String phone;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "account_non_expired", nullable = false)
    private boolean accountNonExpired = true;

    @Column(name = "credentials_non_expired", nullable = false)
    private boolean credentialsNonExpired = true;

    @Column(name = "account_non_locked", nullable = false)
    private boolean accountNonLocked = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "bybit_api_key", length = 255)
    @ToString.Exclude
    private String bybitApiKey;

    @Column(name = "bybit_api_secret", length = 255)
    @ToString.Exclude
    private String bybitApiSecret;

    @Column(name = "pump_enabled", nullable = false)
    private boolean pumpEnabled = true;

    @Column(name = "pump_min_change_percent", nullable = false)
    private double pumpMinChangePercent = 0.8;

    @Column(name = "pump_timeframe", nullable = false, length = 16)
    private String pumpTimeframe = "1m";

    @Column(name = "pump_watchlist", length = 4000)
    private String pumpWatchlist;

    @Column(name = "ema_enabled", nullable = false)
    private boolean emaEnabled = true;

    @Column(name = "ema_fast", nullable = false)
    private int emaFast = 9;

    @Column(name = "ema_slow", nullable = false)
    private int emaSlow = 21;

    @Column(name = "ema_timeframe", nullable = false, length = 16)
    private String emaTimeframe = "1m";

    @Column(name = "ema_watchlist", length = 4000)
    private String emaWatchlist;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", schema = "algo", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Set<UserRole> roles = EnumSet.noneOf(UserRole.class);

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        if (roles.isEmpty()) {
            roles.add(UserRole.USER);
        }
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles;
    }
}
