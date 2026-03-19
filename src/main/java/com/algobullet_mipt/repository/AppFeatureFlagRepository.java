package com.algobullet_mipt.repository;

import com.algobullet_mipt.entity.AppFeatureFlagEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppFeatureFlagRepository extends JpaRepository<AppFeatureFlagEntry, String> {
}
