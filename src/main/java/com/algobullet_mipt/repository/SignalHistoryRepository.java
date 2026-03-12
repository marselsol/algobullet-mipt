package com.algobullet_mipt.repository;

import com.algobullet_mipt.entity.SignalHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface SignalHistoryRepository extends JpaRepository<SignalHistoryEntry, Long> {
    List<SignalHistoryEntry> findByOrderBySignalTimeDescIdDesc(Pageable pageable);

    boolean existsBySourceAndSymbolAndTimeframeAndTypeAndSignalTime(
            String source,
            String symbol,
            String timeframe,
            String type,
            Instant signalTime
    );
}
