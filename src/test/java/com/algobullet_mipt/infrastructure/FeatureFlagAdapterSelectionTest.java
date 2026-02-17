package com.algobullet_mipt.infrastructure;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.domain.market.port.MarketDataPort;
import com.algobullet_mipt.domain.portfolio.port.AccountDataPort;
import com.algobullet_mipt.domain.signal.port.SignalPort;
import com.algobullet_mipt.infrastructure.bybit.BybitAccountDataPort;
import com.algobullet_mipt.infrastructure.bybit.BybitSignalPort;
import com.algobullet_mipt.infrastructure.mock.MockAccountDataPort;
import com.algobullet_mipt.infrastructure.mock.MockSignalPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagAdapterSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    MockSignalPort.class,
                    BybitSignalPort.class,
                    MockAccountDataPort.class,
                    BybitAccountDataPort.class,
                    TestMarketDataConfig.class
            );

    @Test
    void usesMockPortsByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SignalPort.class);
            assertThat(context).hasSingleBean(AccountDataPort.class);
            assertThat(context.getBean(SignalPort.class)).isInstanceOf(MockSignalPort.class);
            assertThat(context.getBean(AccountDataPort.class)).isInstanceOf(MockAccountDataPort.class);
        });
    }

    @Test
    void switchesToBybitPortsWhenFlagsEnabled() {
        contextRunner
                .withPropertyValues(
                        "app.features.use-real-market-data=true",
                        "app.features.use-real-portfolio=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SignalPort.class);
                    assertThat(context).hasSingleBean(AccountDataPort.class);
                    assertThat(context.getBean(SignalPort.class)).isInstanceOf(BybitSignalPort.class);
                    assertThat(context.getBean(AccountDataPort.class)).isInstanceOf(BybitAccountDataPort.class);
                });
    }

    @Configuration
    static class TestMarketDataConfig {
        @Bean
        MarketDataPort marketDataPort() {
            return new MarketDataPort() {
                @Override
                public List<String> getTopUsdtSymbols(int limit) {
                    return List.of("BTCUSDT");
                }

                @Override
                public List<KlineCandle> getRecentKlines(String symbol, String timeframe, int limit) {
                    return List.of();
                }
            };
        }
    }
}
