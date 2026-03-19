package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.portfolio.port.AccountDataPort;
import com.algobullet_mipt.entity.UserAccount;
import com.algobullet_mipt.portfolio.PortfolioAnalysis;
import com.algobullet_mipt.portfolio.PortfolioMetric;
import com.algobullet_mipt.repository.UserRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "app.features", name = "use-real-portfolio", havingValue = "true")
public class BybitAccountDataPort implements AccountDataPort {

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final String baseUrl;
    private final boolean testnet;
    private final long timeoutMs;
    private final String logLevel;

    public BybitAccountDataPort(
            ObjectMapper objectMapper,
            UserRepository userRepository,
            @Value("${app.bybit.base-url:https://api.bybit.com}") String baseUrl,
            @Value("${app.bybit.testnet:false}") boolean testnet,
            @Value("${app.bybit.timeout-ms:5000}") long timeoutMs,
            @Value("${app.bybit.log-level:info}") String logLevel
    ) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.baseUrl = baseUrl;
        this.testnet = testnet;
        this.timeoutMs = timeoutMs;
        this.logLevel = logLevel;
    }

    @Override
    public PortfolioAnalysis getPortfolioAnalysis() {
        PortfolioAnalysis analysis = new PortfolioAnalysis();
        analysis.setGeneratedAt(Instant.now());

        BybitCredentials credentials = resolveCurrentUserCredentials().orElse(null);
        if (credentials == null) {
            analysis.setMetrics(List.of(
                    new PortfolioMetric("SOURCE", "Источник портфеля", "BYBIT API (ключи не заданы)", false,
                            "Введите и сохраните API Key/Secret в разделе подключения на странице портфеля"),
                    new PortfolioMetric("MODE", "Режим", testnet ? "TESTNET" : "MAINNET", true,
                            "После сохранения ключей статус станет 'Подключено'")
            ));
            return analysis;
        }

        try {
            BybitApiAccountRestClient accountClient = new BybitApiAccountRestClientImpl(
                    credentials.apiKey(), credentials.apiSecret(), baseUrl, testnet, timeoutMs, logLevel
            );
            BybitApiPositionRestClient positionClient = new BybitApiPositionRestClientImpl(
                    credentials.apiKey(), credentials.apiSecret(), baseUrl, testnet, timeoutMs, logLevel
            );

            JsonNode walletNode = toJson(accountClient.getWalletBalance(AccountDataRequest.builder()
                    .accountType(AccountType.UNIFIED)
                    .coin("USDT")
                    .build()));

            JsonNode positionsNode = toJson(positionClient.getPositionInfo(PositionDataRequest.builder()
                    .category(CategoryType.LINEAR)
                    .settleCoin("USDT")
                    .limit(200)
                    .build()));

            analysis.setMetrics(buildMetrics(walletNode, positionsNode));
        } catch (Exception ex) {
            analysis.setMetrics(List.of(
                    new PortfolioMetric("SOURCE", "Источник портфеля", "BYBIT API error", false, trimMessage(ex.getMessage())),
                    new PortfolioMetric("MODE", "Режим", testnet ? "TESTNET" : "MAINNET", true,
                            "Проверьте ключи, права API и режим testnet/mainnet")
            ));
        }

        return analysis;
    }

    private Optional<BybitCredentials> resolveCurrentUserCredentials() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String username = authentication.getName();
        if (username == null || username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) {
            return Optional.empty();
        }
        return userRepository.findByUsernameIgnoreCase(username.trim())
                .flatMap(this::toCredentials);
    }

    private Optional<BybitCredentials> toCredentials(UserAccount user) {
        String key = normalizeNullable(user.getBybitApiKey());
        String secret = normalizeNullable(user.getBybitApiSecret());
        if (key == null || secret == null) {
            return Optional.empty();
        }
        return Optional.of(new BybitCredentials(key, secret));
    }

    private JsonNode toJson(Object rawResponse) {
        return objectMapper.valueToTree(rawResponse);
    }

    private List<PortfolioMetric> buildMetrics(JsonNode walletNode, JsonNode positionsNode) {
        assertBybitSuccess(walletNode, "wallet");
        assertBybitSuccess(positionsNode, "positions");

        JsonNode walletList = walletNode.path("result").path("list");
        JsonNode walletAccount = walletList.isArray() && walletList.size() > 0 ? walletList.get(0) : objectMapper.createObjectNode();

        BigDecimal totalEquity = decimal(walletAccount.path("totalEquity"));
        BigDecimal availableBalance = firstNonZero(decimal(walletAccount.path("totalAvailableBalance")), decimal(walletAccount.path("totalMarginBalance")), BigDecimal.ZERO);
        BigDecimal totalWalletBalance = decimal(walletAccount.path("totalWalletBalance"));
        BigDecimal totalPerpUpl = decimal(walletAccount.path("totalPerpUPL"));

        int openPositions = 0;
        int longPositions = 0;
        int shortPositions = 0;
        BigDecimal grossExposure = BigDecimal.ZERO;
        BigDecimal unrealizedPnl = BigDecimal.ZERO;
        BigDecimal largestPositionValue = BigDecimal.ZERO;
        String largestPositionSymbol = "-";

        JsonNode positions = positionsNode.path("result").path("list");
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

                BigDecimal positionValue = firstNonZero(decimal(position.path("positionValue")), decimal(position.path("positionIM")), BigDecimal.ZERO).abs();
                BigDecimal positionUpl = firstNonZero(decimal(position.path("unrealisedPnl")), decimal(position.path("unrealizedPnl")), BigDecimal.ZERO);

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
        metrics.add(new PortfolioMetric("SOURCE", "Источник портфеля", "BYBIT API", true, "Метрики рассчитаны по wallet balance и open positions"));
        metrics.add(new PortfolioMetric("MODE", "Режим", testnet ? "TESTNET" : "MAINNET", true, "Проверьте соответствие ключей окружению"));
        metrics.add(new PortfolioMetric("EQUITY", "Общий капитал", formatUsd(totalEquity), totalEquity.signum() > 0, "Суммарная оценка аккаунта с учетом текущего результата"));
        metrics.add(new PortfolioMetric("WALLET", "Баланс кошелька", formatUsd(totalWalletBalance), true, "Баланс без учета нереализованного PnL"));
        metrics.add(new PortfolioMetric("AVAILABLE", "Свободный баланс", formatUsd(availableBalance), availableBalance.signum() >= 0, "Средства, доступные для новых сделок"));
        metrics.add(new PortfolioMetric("AV_SHARE", "Свободные средства / капитал", availableSharePct + "%", availableSharePct.compareTo(BigDecimal.valueOf(20)) >= 0, "Доля ликвидности относительно капитала"));
        metrics.add(new PortfolioMetric("UPL", "Unrealized PnL", formatUsd(unrealizedPnl), unrealizedPnl.compareTo(BigDecimal.ZERO) >= 0, "Суммарный нереализованный результат"));
        metrics.add(new PortfolioMetric("POS_CNT", "Открытые позиции", String.valueOf(openPositions), openPositions <= 12, "Количество одновременно открытых позиций"));
        metrics.add(new PortfolioMetric("LONG_SHORT", "Long / Short", longPositions + " / " + shortPositions, true, "Баланс позиций в лонг и шорт"));
        metrics.add(new PortfolioMetric("EXPOSURE", "Общий объём позиций", formatUsd(grossExposure), exposureToEquityPct.compareTo(BigDecimal.valueOf(300)) <= 0, "Суммарный размер всех открытых позиций"));
        metrics.add(new PortfolioMetric("EXP_EQUITY", "Нагрузка на капитал", exposureToEquityPct + "%", exposureToEquityPct.compareTo(BigDecimal.valueOf(250)) <= 0, "Отношение объема позиций к капиталу, косвенно отражает риск"));
        metrics.add(new PortfolioMetric("LARGEST", "Крупнейшая позиция", largestPositionSymbol + " " + formatUsd(largestPositionValue), true, "Самая большая позиция по объему, показатель концентрации риска"));
        metrics.add(new PortfolioMetric("UPL_WALLET", "Bybit Total Perp UPL", formatUsd(totalPerpUpl), totalPerpUpl.compareTo(BigDecimal.ZERO) >= 0, "Нереализованный PnL из wallet summary Bybit"));
        return metrics;
    }

    private void assertBybitSuccess(JsonNode node, String source) {
        int retCode = node.path("retCode").asInt(0);
        if (retCode != 0) {
            throw new IllegalStateException("Bybit " + source + " error: " + retCode + " " + node.path("retMsg").asText("Unknown error"));
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
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(BigDecimal.ZERO) != 0) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    private String formatUsd(BigDecimal value) {
        BigDecimal scaled = (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        return "$" + String.format(Locale.US, "%,.2f", scaled);
    }

    private String trimMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Неизвестная ошибка";
        }
        return message.length() > 140 ? message.substring(0, 140) : message;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private record BybitCredentials(String apiKey, String apiSecret) {
    }
}
