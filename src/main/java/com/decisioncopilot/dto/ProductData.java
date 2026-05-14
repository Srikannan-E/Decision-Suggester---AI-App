package com.decisioncopilot.dto;

import java.math.BigDecimal;

public record ProductData(
    String name,
    BigDecimal price,
    BigDecimal rating,
    int reviewCount,
    String category,
    String buyerBudget,
    String buyerQuestion
) {
    public ProductData(String name, BigDecimal price, BigDecimal rating, int reviewCount, String category) {
        this(name, price, rating, reviewCount, category, null, null);
    }
}