package com.algobullet_mipt.repository;

import com.algobullet_mipt.entity.UserPumpSettingsEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPumpSettingsRepository extends JpaRepository<UserPumpSettingsEntry, Long> {
}
