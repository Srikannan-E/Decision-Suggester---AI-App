package com.decisioncopilot.dto;

import java.math.BigDecimal;

public record ProductData(
    String name,
    BigDecimal price,
    BigDecimal rating,
    int reviewCount,
    String category
) {}