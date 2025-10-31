package com.algobullet_mipt.portfolio;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class PortfolioAnalysisService {

    // Заглушка: возвращает фиктивные метрики анализа портфеля
    public PortfolioAnalysis getStubAnalysis() {
        PortfolioAnalysis analysis = new PortfolioAnalysis();
        analysis.setGeneratedAt(Instant.now());

        // Примерные значения для демонстрации
        List<PortfolioMetric> metrics = List.of(
                new PortfolioMetric(
                        "SHARPE",
                        "Sharpe Ratio",
                        "1.25",
                        true,
                        "Sharpe > 1 — приемлемый риск/доходность"
                ),
                new PortfolioMetric(
                        "MDD",
                        "Максимальная просадка",
                        "-18%",
                        true,
                        "Просадка < 20% — ок для умеренного риска"
                ),
                new PortfolioMetric(
                        "CORR",
                        "Корреляция активов (средняя)",
                        "0.42",
                        false,
                        "Высокая корреляция — добавить некоррелирующие активы"
                ),
                new PortfolioMetric(
                        "MPT",
                        "Модель Марковица (MPT)",
                        "Целевое соотношение риск/качество: 70/30",
                        true,
                        "Ребаланс к 70/30 снижает волатильность портфеля"
                ),
                new PortfolioMetric(
                        "VaR",
                        "Value at Risk (95%)",
                        "-6.4%",
                        false,
                        "Снизить плечо/риск — VaR выше комфортного уровня"
                ),
                new PortfolioMetric(
                        "STABLE",
                        "Доля стейблкоинов",
                        "12%",
                        true,
                        "Подушка ликвидности достаточна для выкупа просадок"
                ),
                new PortfolioMetric(
                        "TREYNOR",
                        "Доходность на единицу рыночного риска",
                        "0.09",
                        false,
                        "Оптимизировать бета-риск или повысить альфу"
                )
        );

        analysis.setMetrics(metrics);
        return analysis;
    }
}

