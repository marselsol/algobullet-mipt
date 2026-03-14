package com.algobullet_mipt.experiment.bybitlatency;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BybitLatencyMeasurementWriterTest {

    @Test
    void writesMeasurementWithCalculatedLatencies() {
        BybitLatencyMeasurementRepository repository = mock(BybitLatencyMeasurementRepository.class);
        BybitLatencyExperimentRunIdProvider runIdProvider = new BybitLatencyExperimentRunIdProvider();
        BybitLatencyMeasurementWriter writer = new BybitLatencyMeasurementWriter(repository, runIdProvider);

        when(repository.existsByRunIdAndTransportAndSymbolAndTimeframeAndSignalTime(
                any(), any(), any(), any(), any()
        )).thenReturn(false);

        BybitLatencySignal signal = new BybitLatencySignal(
                "BTCUSDT",
                "1m",
                "PUMP",
                Instant.parse("2026-03-14T10:01:00Z"),
                Instant.parse("2026-03-14T10:02:00Z"),
                2.0d,
                "changePercent=2.0000"
        );

        writer.write(
                BybitLatencyExperimentTransport.WS,
                signal,
                Instant.parse("2026-03-14T10:02:05Z"),
                Instant.parse("2026-03-14T10:02:07Z")
        );

        ArgumentCaptor<BybitLatencyMeasurementEntry> captor = ArgumentCaptor.forClass(BybitLatencyMeasurementEntry.class);
        verify(repository).save(captor.capture());
        BybitLatencyMeasurementEntry entry = captor.getValue();

        assertThat(entry.getTransport()).isEqualTo("WS");
        assertThat(entry.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(entry.getDetectionLatencyMs()).isEqualTo(5000L);
        assertThat(entry.getEmissionLatencyMs()).isEqualTo(7000L);
    }
}
