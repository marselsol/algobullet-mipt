package com.algobullet_mipt.infrastructure.mock;

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
        havingValue = "false",
        matchIfMissing = true
)
public class MockAccountDataPort implements AccountDataPort {

    @Override
    public PortfolioAnalysis getPortfolioAnalysis() {
        PortfolioAnalysis analysis = new PortfolioAnalysis();
        analysis.setGeneratedAt(Instant.now());

        List<PortfolioMetric> metrics = List.of(
                new PortfolioMetric(
                        "SHARPE",
                        "Sharpe Ratio",
                        "1.25",
                        true,
                        "Sharpe > 1 means acceptable risk/reward"
                ),
                new PortfolioMetric(
                        "MDD",
                        "Max Drawdown",
                        "-18%",
                        true,
                        "Drawdown below 20% is acceptable for moderate risk"
                ),
                new PortfolioMetric(
                        "CORR",
                        "Average Asset Correlation",
                        "0.42",
                        false,
                        "Correlation is high, add less-correlated assets"
                ),
                new PortfolioMetric(
                        "MPT",
                        "Markowitz Model (MPT)",
                        "Target risk/quality split: 70/30",
                        true,
                        "Rebalancing to 70/30 can reduce portfolio volatility"
                ),
                new PortfolioMetric(
                        "VaR",
                        "Value at Risk (95%)",
                        "-6.4%",
                        false,
                        "Reduce leverage/risk, VaR is above comfort level"
                ),
                new PortfolioMetric(
                        "STABLE",
                        "Stablecoin Share",
                        "12%",
                        true,
                        "Liquidity buffer is enough for buying drawdowns"
                ),
                new PortfolioMetric(
                        "TREYNOR",
                        "Return per Unit of Market Risk",
                        "0.09",
                        false,
                        "Optimize beta-risk or improve alpha"
                )
        );

        analysis.setMetrics(metrics);
        return analysis;
    }
}
