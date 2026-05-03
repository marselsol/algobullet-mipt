package com.algobullet_mipt.entity;

public enum SubscriptionPlan {
    FREE("Free", "0 ₽", "Базовый доступ к сервису"),
    B2C_BASIC("B2C Basic", "1 490 ₽", "20+ пар, сигналы, аналитика"),
    B2C_PRO("B2C Pro", "2 990 ₽", "100+ пар, памп-скринер, API"),
    B2B_API("B2B API", "от 50k", "Индивидуальные условия для компаний");

    private final String displayName;
    private final String priceLabel;
    private final String shortDescription;

    SubscriptionPlan(String displayName, String priceLabel, String shortDescription) {
        this.displayName = displayName;
        this.priceLabel = priceLabel;
        this.shortDescription = shortDescription;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPriceLabel() {
        return priceLabel;
    }

    public String getShortDescription() {
        return shortDescription;
    }
}
