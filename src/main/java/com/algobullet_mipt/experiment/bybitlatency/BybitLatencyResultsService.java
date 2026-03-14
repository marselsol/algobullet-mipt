package com.algobullet_mipt.experiment.bybitlatency;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BybitLatencyResultsService {

    private static final DateTimeFormatter UI_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss:SSS").withZone(ZoneId.systemDefault());

    private final BybitLatencyMeasurementRepository repository;
    private final BybitLatencyExperimentRunIdProvider runIdProvider;
    private final BybitLatencyExperimentProperties properties;

    public BybitLatencyResultsService(
            BybitLatencyMeasurementRepository repository,
            BybitLatencyExperimentRunIdProvider runIdProvider,
            BybitLatencyExperimentProperties properties
    ) {
        this.repository = repository;
        this.runIdProvider = runIdProvider;
        this.properties = properties;
    }

    public ResultsView getResults(String requestedRunId) {
        String currentRunId = runIdProvider.getRunId();
        List<BybitLatencyMeasurementEntry> latestMeasurements = repository.findTop200ByOrderByCreatedAtDesc();

        Set<String> availableRunIds = latestMeasurements.stream()
                .map(BybitLatencyMeasurementEntry::getRunId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        availableRunIds.add(currentRunId);

        String selectedRunId = requestedRunId;
        if (selectedRunId == null || selectedRunId.isBlank()) {
            selectedRunId = availableRunIds.contains(currentRunId)
                    ? currentRunId
                    : availableRunIds.stream().findFirst().orElse(currentRunId);
        }

        List<BybitLatencyMeasurementEntry> measurements = repository.findTop200ByRunIdOrderByCreatedAtDesc(selectedRunId);
        List<TransportSummary> summaries = buildSummaries(measurements);
        Map<String, Long> averageDetectionByTransport = summaries.stream()
                .collect(Collectors.toMap(TransportSummary::transport, TransportSummary::avgDetectionMs));

        return new ResultsView(
                currentRunId,
                selectedRunId,
                List.copyOf(availableRunIds),
                properties.getMode().name(),
                properties.getSymbols(),
                properties.getTimeframe(),
                properties.getPollingDelayMs(),
                summaries,
                measurements.stream()
                        .map(entry -> toMeasurementRow(entry, averageDetectionByTransport))
                        .toList()
        );
    }

    private List<TransportSummary> buildSummaries(List<BybitLatencyMeasurementEntry> measurements) {
        Map<String, List<BybitLatencyMeasurementEntry>> byTransport = measurements.stream()
                .collect(Collectors.groupingBy(BybitLatencyMeasurementEntry::getTransport));

        return byTransport.entrySet().stream()
                .map(entry -> toTransportSummary(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(TransportSummary::transport))
                .toList();
    }

    private TransportSummary toTransportSummary(String transport, List<BybitLatencyMeasurementEntry> entries) {
        DoubleSummaryStatistics detectionStats = entries.stream()
                .collect(Collectors.summarizingDouble(BybitLatencyMeasurementEntry::getDetectionLatencyMs));
        DoubleSummaryStatistics emissionStats = entries.stream()
                .collect(Collectors.summarizingDouble(BybitLatencyMeasurementEntry::getEmissionLatencyMs));

        Instant lastDetectedAt = entries.stream()
                .map(BybitLatencyMeasurementEntry::getDetectedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new TransportSummary(
                transport,
                entries.size(),
                Math.round(detectionStats.getAverage()),
                entries.isEmpty() ? 0L : Math.round(detectionStats.getMin()),
                entries.isEmpty() ? 0L : Math.round(detectionStats.getMax()),
                Math.round(emissionStats.getAverage()),
                entries.isEmpty() ? 0L : Math.round(emissionStats.getMin()),
                entries.isEmpty() ? 0L : Math.round(emissionStats.getMax()),
                lastDetectedAt
        );
    }

    private MeasurementRow toMeasurementRow(
            BybitLatencyMeasurementEntry entry,
            Map<String, Long> averageDetectionByTransport
    ) {
        Long oppositeAverageMs = averageDetectionByTransport.get(oppositeTransport(entry.getTransport()));
        String comparisonText = buildComparisonText(entry.getTransport(), entry.getDetectionLatencyMs(), oppositeAverageMs);

        return new MeasurementRow(
                entry.getCreatedAt(),
                entry.getTransport(),
                entry.getSymbol(),
                entry.getTimeframe(),
                entry.getSignalType(),
                entry.getSignalTime(),
                entry.getDetectedAt(),
                entry.getEmissionLatencyMs(),
                entry.getDetectionLatencyMs(),
                buildExplanation(entry, comparisonText)
        );
    }

    private String buildExplanation(BybitLatencyMeasurementEntry entry, String comparisonText) {
        String baseText = "Время закрытия свечи - %s. Время обнаружения свечи - %s. Время от закрытия до обнаружения - %d мс."
                .formatted(
                        formatUiTime(entry.getSignalTime()),
                        formatUiTime(entry.getDetectedAt()),
                        entry.getDetectionLatencyMs()
                );
        return comparisonText == null || comparisonText.isBlank()
                ? baseText
                : baseText + " " + comparisonText;
    }

    private String formatUiTime(Instant value) {
        return value == null ? "-" : UI_TIME_FORMATTER.format(value);
    }

    private String buildComparisonText(String transport, long detectionLatencyMs, Long oppositeAverageMs) {
        if (oppositeAverageMs == null || oppositeAverageMs <= 0) {
            return "Недостаточно данных для сравнения со средним значением другого источника.";
        }

        long percent = Math.round((double) detectionLatencyMs * 100.0 / oppositeAverageMs);
        return "Это %d%% от среднего времени обнаружения через %s (%d мс)."
                .formatted(percent, oppositeTransportLabel(transport), oppositeAverageMs);
    }

    private String oppositeTransport(String transport) {
        return switch (transport) {
            case "REST" -> "WS";
            case "WS" -> "REST";
            default -> "";
        };
    }

    private String oppositeTransportLabel(String transport) {
        return switch (transport) {
            case "REST" -> "WebSocket";
            case "WS" -> "REST";
            default -> "другой источник";
        };
    }

    public record ResultsView(
            String currentRunId,
            String selectedRunId,
            List<String> availableRunIds,
            String mode,
            List<String> symbols,
            String timeframe,
            long pollingDelayMs,
            List<TransportSummary> summaries,
            List<MeasurementRow> measurements
    ) {
        public String modeLabel() {
            return switch (mode) {
                case "REST" -> "Только REST";
                case "WS" -> "Только WebSocket";
                case "DUAL" -> "Одновременное сравнение REST и WebSocket";
                default -> mode;
            };
        }
    }

    public record TransportSummary(
            String transport,
            int signals,
            long avgDetectionMs,
            long minDetectionMs,
            long maxDetectionMs,
            long avgEmissionMs,
            long minEmissionMs,
            long maxEmissionMs,
            Instant lastDetectedAt
    ) {
        public String transportLabel() {
            return switch (transport) {
                case "REST" -> "REST";
                case "WS" -> "WebSocket";
                default -> transport;
            };
        }
    }

    public record MeasurementRow(
            Instant createdAt,
            String transport,
            String symbol,
            String timeframe,
            String signalType,
            Instant signalTime,
            Instant detectedAt,
            long emissionLatencyMs,
            long detectionLatencyMs,
            String explanation
    ) {
        public String transportLabel() {
            return switch (transport) {
                case "REST" -> "REST";
                case "WS" -> "WebSocket";
                default -> transport;
            };
        }
    }
}
