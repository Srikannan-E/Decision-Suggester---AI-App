package com.decisioncopilot.dto;

import java.math.BigDecimal;

public record LlmDecisionResult(
    String verdict,
    BigDecimal confidenceScore,
    String[] pros,
    String[] cons,
    String summary,
    String reasoning
) {}