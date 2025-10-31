package com.algobullet_mipt.portfolio;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class PortfolioAnalysis {
    private Instant generatedAt;
    private List<PortfolioMetric> metrics;
}

