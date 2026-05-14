package com.decisioncopilot.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DecisionResponse(
    UUID id,
    String status,
    String productName,
    BigDecimal price,
    BigDecimal rating,
    String verdict,
    BigDecimal confidenceScore,
    String[] pros,
    String[] cons,
    String summary,
    String reasoning,
    Long processingTimeMs,
    LocalDateTime createdAt,
    String errorMessage
) {}