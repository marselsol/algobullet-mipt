package com.algobullet_mipt.payment.yookassa;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.yookassa")
public class YooKassaProperties {

    private boolean enabled = false;
    private String apiBaseUrl = "https://api.yookassa.ru/v3";
    private String shopId;
    private String secretKey;
    private String returnUrl = "http://localhost:2223/checkout";

    public boolean isConfigured() {
        return enabled
                && shopId != null && !shopId.isBlank()
                && secretKey != null && !secretKey.isBlank()
                && returnUrl != null && !returnUrl.isBlank();
    }
}
