package com.wayt.domain;

public enum SubscriptionTier {
    FREE(0, 0),
    PLUS(2900, 10),
    PRO(4900, 3);

    private final int monthlyPriceKrw;
    private final int autoEtaRefreshMinutes;

    SubscriptionTier(int monthlyPriceKrw, int autoEtaRefreshMinutes) {
        this.monthlyPriceKrw = monthlyPriceKrw;
        this.autoEtaRefreshMinutes = autoEtaRefreshMinutes;
    }

    public int getMonthlyPriceKrw() {
        return monthlyPriceKrw;
    }

    public int getAutoEtaRefreshMinutes() {
        return autoEtaRefreshMinutes;
    }
}
