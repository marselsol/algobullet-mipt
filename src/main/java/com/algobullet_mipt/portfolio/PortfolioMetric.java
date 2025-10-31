package com.algobullet_mipt.portfolio;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PortfolioMetric {
    private String code;            // e.g. SHARPE, MDD, CORR
    private String name;            // Display name
    private String value;           // Formatted value
    private boolean passed;         // Pass/fail for highlighting
    private String recommendation;  // Short suggestion
}

