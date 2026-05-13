package com.decisioncopilot.dto;

import java.util.List;

public record DecisionHistoryResponse(
    List<DecisionResponse> decisions,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}