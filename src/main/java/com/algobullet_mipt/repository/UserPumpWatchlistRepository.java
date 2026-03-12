package com.algobullet_mipt.repository;

import com.algobullet_mipt.entity.UserPumpWatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPumpWatchlistRepository extends JpaRepository<UserPumpWatchlistEntry, Long> {
    List<UserPumpWatchlistEntry> findByUserIdOrderByIdAsc(Long userId);

    void deleteByUserId(Long userId);
}
