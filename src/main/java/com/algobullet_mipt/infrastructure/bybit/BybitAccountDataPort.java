package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.portfolio.port.AccountDataPort;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.account.AccountType;
import com.bybit.api.client.domain.account.request.AccountDataRequest;
import com.bybit.api.client.domain.position.request.PositionDataRequest;
import com.bybit.api.client.impl.BybitApiAccountRestClientImpl;
import com.bybit.api.client.impl.BybitApiPositionRestClientImpl;
import com.bybit.api.client.restApi.BybitApiAccountRestClient;
import com.bybit.api.client.restApi.BybitApiPositionRestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.algobullet_mipt.portfolio.PortfolioAnalysis;
import com.algobullet_mipt.portfolio.PortfolioMetric;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(
        prefix = "app.features",
        name = "use-real-portfolio",
        havingValue = "true"
)
public class BybitAccountDataPort implements AccountDataPort {

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiSecret;
    private final String baseUrl;
    private final boolean testnet;
    private final long timeoutMs;
    private final String logLevel;

    public BybitAccountDataPort(
            ObjectMapper objectMapper,
            @Value("${app.bybit.api-key:}") String apiKey,
            @Value("${app.bybit.api-secret:}") String apiSecret,
            @Value("${app.bybit.base-url:https://api.bybit.com}") String baseUrl,
            @Value("${app.bybit.testnet:false}") boolean testnet,
            @Value("${app.bybit.timeout-ms:5000}") long timeoutMs,
            @Value("${app.bybit.log-level:info}") String logLevel
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.baseUrl = baseUrl;
        this.testnet = testnet;
        this.timeoutMs = timeoutMs;
        this.logLevel = logLevel;
    }

    @Override
    public PortfolioAnalysis getPortfolioAnalysis() {
        PortfolioAnalysis analysis = new PortfolioAnalysis();
        analysis.setGeneratedAt(Instant.now());

        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            analysis.setMetrics(List.of(
                    new PortfolioMetric("SOURCE", "Источник портфеля", "BYBIT API (ключи не заданы)", false,
                            "Добавьте app.bybit.api-key и app.bybit.api-secret в application-local.properties"),
                    new PortfolioMetric("MODE", "Режим", testnet ? "TESTNET" : "MAINNET", true,
                            "После добавления ключей метрики будут считаться из кошелька и позиций")
            ));
            return analysis;
        }

        try {
            BybitApiAccountRestClient accountClient = new BybitApiAccountRestClientImpl(
                    apiKey, apiSecret, baseUrl, testnet, timeoutMs, logLevel
            );
            BybitApiPositionRestClient positionClient = new BybitApiPositionRestClientImpl(
                    apiKey, apiSecret, baseUrl, testnet, timeoutMs, logLevel
            );

            JsonNode walletNode = loadWallet(accountClient);
            JsonNode positionsNode = loadPositions(positionClient);
            analysis.setMetrics(buildMetrics(walletNode, positionsNode));
        } catch (Exception ex) {
            analysis.setMetrics(List.of(
                    new PortfolioMetric("SOURCE", "Источник портфеля", "BYBIT API error", false,
                            trimMessage(ex.getMessage())),
                    new PortfolioMetric("MODE", "Режим", testnet ? "TESTNET" : "MAINNET", true,
                            "Проверьте ключи, права API и доступ к аккаунту Bybit")
            ));
        }

        return analysis;
    }

    private JsonNode loadWallet(BybitApiAccountRestClient accountClient) {
        AccountDataRequest request = AccountDataRequest.builder()
                .accountType(AccountType.UNIFIED)
                .coin("USDT")
                .build();
        return toJson(accountClient.getWalletBalance(request));
    }

    private JsonNode loadPositions(BybitApiPositionRestClient positionClient) {
        PositionDataRequest request = PositionDataRequest.builder()
                .category(CategoryType.LINEAR)
                .settleCoin("USDT")
                .limit(200)
                .build();
        return toJson(positionClient.getPositionInfo(request));
    }

    private JsonNode toJson(Object rawResponse) {
        return objectMapper.valueToTree(rawResponse);
    }

    private List<PortfolioMetric> buildMetrics(JsonNode walletNode, JsonNode positionsNode) {
        assertBybitSuccess(walletNode, "wallet");
        assertBybitSuccess(positionsNode, "positions");

        JsonNode walletAccount = walletNode.path("result").path("list").isArray() && walletNode.path("result").path("list").size() > 0
                ? walletNode.path("result").path("list").get(0)
                : objectMapper.createObjectNode();

        BigDecimal totalEquity = decimal(walletAccount.path("totalEquity"));
        BigDecimal availableBalance = firstNonZero(
                decimal(walletAccount.path("totalAvailableBalance")),
                decimal(walletAccount.path("totalMarginBalance")),
                BigDecimal.ZERO
        );
        BigDecimal totalWalletBalance = decimal(walletAccount.path("totalWalletBalance"));
        BigDecimal totalPerpUpl = decimal(walletAccount.path("totalPerpUPL"));

        JsonNode positions = positionsNode.path("result").path("list");
        int openPositions = 0;
        int longPositions = 0;
        int shortPositions = 0;
        BigDecimal grossExposure = BigDecimal.ZERO;
        BigDecimal unrealizedPnl = BigDecimal.ZERO;
        BigDecimal largestPositionValue = BigDecimal.ZERO;
        String largestPositionSymbol = "-";

        if (positions.isArray()) {
            for (JsonNode position : positions) {
                BigDecimal size = decimal(position.path("size"));
                if (size.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                openPositions++;
                String side = position.path("side").asText("");
                if ("Buy".equalsIgnoreCase(side)) {
                    longPositions++;
                } else if ("Sell".equalsIgnoreCase(side)) {
                    shortPositions++;
                }

                BigDecimal positionValue = firstNonZero(
                        decimal(position.path("positionValue")),
                        decimal(position.path("positionIM")),
                        BigDecimal.ZERO
                ).abs();
                BigDecimal positionUpl = firstNonZero(
                        decimal(position.path("unrealisedPnl")),
                        decimal(position.path("unrealizedPnl")),
                        BigDecimal.ZERO
                );

                grossExposure = grossExposure.add(positionValue);
                unrealizedPnl = unrealizedPnl.add(positionUpl);

                if (positionValue.compareTo(largestPositionValue) > 0) {
                    largestPositionValue = positionValue;
                    largestPositionSymbol = position.path("symbol").asText("-");
                }
            }
        }

        BigDecimal exposureToEquityPct = totalEquity.signum() > 0
                ? grossExposure.multiply(BigDecimal.valueOf(100)).divide(totalEquity, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal availableSharePct = totalEquity.signum() > 0
                ? availableBalance.multiply(BigDecimal.valueOf(100)).divide(totalEquity, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<PortfolioMetric> metrics = new ArrayList<>();
        metrics.add(new PortfolioMetric("SOURCE", "Источник портфеля", "BYBIT API", true,
                "Метрики рассчитаны по wallet balance и open positions"));
        metrics.add(new PortfolioMetric("MODE", "Режим", testnet ? "TESTNET" : "MAINNET", true,
                "Убедитесь, что ключи соответствуют выбранному окружению"));
        metrics.add(new PortfolioMetric("EQUITY", "Total Equity", formatUsd(totalEquity), totalEquity.signum() > 0,
                "Суммарная оценка аккаунта"));
        metrics.add(new PortfolioMetric("WALLET", "Wallet Balance", formatUsd(totalWalletBalance), true,
                "Баланс без учета нереализованного PnL"));
        metrics.add(new PortfolioMetric("AVAILABLE", "Available Balance", formatUsd(availableBalance), availableBalance.signum() >= 0,
                "Свободная маржа для новых сделок"));
        metrics.add(new PortfolioMetric("AV_SHARE", "Available / Equity", availableSharePct + "%", availableSharePct.compareTo(BigDecimal.valueOf(20)) >= 0,
                "Держите запас ликвидности под волатильность"));
        metrics.add(new PortfolioMetric("UPL", "Unrealized PnL", formatUsd(unrealizedPnl), unrealizedPnl.compareTo(BigDecimal.ZERO) >= 0,
                "Суммарный нереализованный результат открытых позиций"));
        metrics.add(new PortfolioMetric("POS_CNT", "Open Positions", String.valueOf(openPositions), openPositions <= 12,
                "Слишком много позиций усложняет контроль риска"));
        metrics.add(new PortfolioMetric("LONG_SHORT", "Long / Short", longPositions + " / " + shortPositions, true,
                "Следите за перекосом в одну сторону рынка"));
        metrics.add(new PortfolioMetric("EXPOSURE", "Gross Exposure", formatUsd(grossExposure), exposureToEquityPct.compareTo(BigDecimal.valueOf(300)) <= 0,
                "Общий размер позиций относительно equity"));
        metrics.add(new PortfolioMetric("EXP_EQUITY", "Exposure / Equity", exposureToEquityPct + "%", exposureToEquityPct.compareTo(BigDecimal.valueOf(250)) <= 0,
                "Высокое плечо повышает риск ликвидаций"));
        metrics.add(new PortfolioMetric("LARGEST", "Largest Position", largestPositionSymbol + " " + formatUsd(largestPositionValue), true,
                "Проверьте концентрацию риска в одном активе"));
        metrics.add(new PortfolioMetric("UPL_WALLET", "Bybit Total Perp UPL", formatUsd(totalPerpUpl), totalPerpUpl.compareTo(BigDecimal.ZERO) >= 0,
                "Значение из wallet summary Bybit"));
        return metrics;
    }

    private void assertBybitSuccess(JsonNode node, String source) {
        int retCode = node.path("retCode").asInt(0);
        if (retCode != 0) {
            String message = node.path("retMsg").asText("Unknown error");
            throw new IllegalStateException("Bybit " + source + " error: " + retCode + " " + message);
        }
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        String raw = node.asText("");
        if (raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal firstNonZero(BigDecimal... values) {
        if (values == null) {
            return BigDecimal.ZERO;
        }
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(BigDecimal.ZERO) != 0) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    private String formatUsd(BigDecimal value) {
        if (value == null) {
            return "$0.00";
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return "$" + String.format(Locale.US, "%,.2f", scaled);
    }

    private String trimMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Неизвестная ошибка";
        }
        return message.length() > 140 ? message.substring(0, 140) : message;
    }
}
