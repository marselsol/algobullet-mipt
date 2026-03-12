package com.algobullet_mipt.repository;

import com.algobullet_mipt.entity.UserEmaSettingsEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEmaSettingsRepository extends JpaRepository<UserEmaSettingsEntry, Long> {
}
