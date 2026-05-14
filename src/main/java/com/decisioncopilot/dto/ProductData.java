package com.decisioncopilot.dto;

import java.math.BigDecimal;

public record ProductData(
    String name,
    BigDecimal price,
    BigDecimal rating,
    int reviewCount,
    String category,
    String buyerBudget,
    String buyerQuestion,
    String[] featureHighlights,
    String specSummary
) {
    public ProductData(String name, BigDecimal price, BigDecimal rating, int reviewCount, String category) {
        this(name, price, rating, reviewCount, category, null, null, new String[0], null);
    }
}