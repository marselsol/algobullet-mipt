package com.algobullet_mipt.entity;

import java.math.BigDecimal;

public enum SubscriptionPlan {
    FREE("Free", "0 ₽", null, "Базовый доступ к сервису", false, null),
    B2C_BASIC("B2C Basic", "199 ₽", "1 490 ₽", "20+ пар, сигналы, аналитика", false, new BigDecimal("199.00")),
    B2C_PRO("B2C Pro", "499 ₽", "2 990 ₽", "100+ пар, памп-скринер, API", false, new BigDecimal("499.00")),
    B2B_API("B2B API", "от 50k", null, "Индивидуальные условия для компаний", true, null);

    private final String displayName;
    private final String priceLabel;
    private final String oldPriceLabel;
    private final String shortDescription;
    private final boolean contactOnly;
    private final BigDecimal amountValue;

    SubscriptionPlan(String displayName,
                     String priceLabel,
                     String oldPriceLabel,
                     String shortDescription,
                     boolean contactOnly,
                     BigDecimal amountValue) {
        this.displayName = displayName;
        this.priceLabel = priceLabel;
        this.oldPriceLabel = oldPriceLabel;
        this.shortDescription = shortDescription;
        this.contactOnly = contactOnly;
        this.amountValue = amountValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPriceLabel() {
        return priceLabel;
    }

    public String getOldPriceLabel() {
        return oldPriceLabel;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public boolean isContactOnly() {
        return contactOnly;
    }

    public boolean hasDiscount() {
        return oldPriceLabel != null && !oldPriceLabel.isBlank();
    }

    public BigDecimal getAmountValue() {
        return amountValue;
    }

    public boolean isPayable() {
        return amountValue != null;
    }
}
