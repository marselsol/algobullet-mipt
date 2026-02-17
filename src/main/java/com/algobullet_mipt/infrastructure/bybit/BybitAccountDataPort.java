package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.portfolio.port.AccountDataPort;
import com.algobullet_mipt.portfolio.PortfolioAnalysis;
import com.algobullet_mipt.portfolio.PortfolioMetric;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "app.features",
        name = "use-real-portfolio",
        havingValue = "true"
)
public class BybitAccountDataPort implements AccountDataPort {

    @Override
    public PortfolioAnalysis getPortfolioAnalysis() {
        // TODO: replace with Bybit account + position based analytics.
        PortfolioAnalysis analysis = new PortfolioAnalysis();
        analysis.setGeneratedAt(Instant.now());
        analysis.setMetrics(List.of(
                new PortfolioMetric("SOURCE", "Portfolio Source", "BYBIT (stub)", true, "Real adapter is wired")
        ));
        return analysis;
    }
}
