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
                        "Коэффициент Sharpe",
                        "1.25",
                        true,
                        "Sharpe > 1 обычно означает приемлемое соотношение риска и доходности"
                ),
                new PortfolioMetric(
                        "MDD",
                        "Максимальная просадка",
                        "-18%",
                        true,
                        "Просадка ниже 20% обычно считается приемлемой для умеренного риска"
                ),
                new PortfolioMetric(
                        "CORR",
                        "Средняя корреляция активов",
                        "0.42",
                        false,
                        "Корреляция повышенная, стоит добавить менее зависимые активы"
                ),
                new PortfolioMetric(
                        "MPT",
                        "Модель Markowitz (MPT)",
                        "Целевое распределение риск/качество: 70/30",
                        true,
                        "Ребалансировка к 70/30 может снизить волатильность портфеля"
                ),
                new PortfolioMetric(
                        "VaR",
                        "Value at Risk (95%)",
                        "-6.4%",
                        false,
                        "Стоит снизить плечо или общий риск, VaR выше комфортного уровня"
                ),
                new PortfolioMetric(
                        "STABLE",
                        "Доля стейблкоинов",
                        "12%",
                        true,
                        "Буфера ликвидности достаточно для добора на просадках"
                ),
                new PortfolioMetric(
                        "TREYNOR",
                        "Доходность на единицу рыночного риска",
                        "0.09",
                        false,
                        "Нужно улучшить alpha или сократить beta-риск"
                )
        );

        analysis.setMetrics(metrics);
        return analysis;
    }
}
