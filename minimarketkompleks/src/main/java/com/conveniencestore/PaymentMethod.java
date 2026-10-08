package com.conveniencestore;

public enum PaymentMethod {
    CASH_ON_DELIVERY("Cash on Delivery"),
    GCASH("GCash"),
    CARD("Card");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
