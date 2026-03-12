package com.algobullet_mipt.repository;

import com.algobullet_mipt.entity.UserEmaWatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserEmaWatchlistRepository extends JpaRepository<UserEmaWatchlistEntry, Long> {
    List<UserEmaWatchlistEntry> findByUserIdOrderByIdAsc(Long userId);

    void deleteByUserId(Long userId);
}
