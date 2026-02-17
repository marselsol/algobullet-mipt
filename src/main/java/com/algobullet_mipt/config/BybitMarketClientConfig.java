package com.algobullet_mipt.config;

import com.bybit.api.client.impl.BybitApiMarketRestClientImpl;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BybitMarketClientConfig {

    @Bean
    @ConditionalOnProperty(
            prefix = "app.features",
            name = "use-real-market-data",
            havingValue = "true"
    )
    public BybitApiMarketRestClient bybitApiMarketRestClient(
            @Value("${app.bybit.base-url:https://api.bybit.com}") String baseUrl,
            @Value("${app.bybit.testnet:false}") boolean testnet,
            @Value("${app.bybit.timeout-ms:5000}") long timeoutMs,
            @Value("${app.bybit.log-level:info}") String logLevel
    ) {
        return new BybitApiMarketRestClientImpl(baseUrl, testnet, timeoutMs, logLevel);
    }
}
