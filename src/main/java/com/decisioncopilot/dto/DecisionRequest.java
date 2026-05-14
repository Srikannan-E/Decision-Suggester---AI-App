package com.decisioncopilot.dto;

import jakarta.validation.constraints.NotBlank;

public record DecisionRequest(
    @NotBlank String input,
    @NotBlank String category,
    String budget,
    String userQuestion
) {
    public DecisionRequest {
        if (budget != null) {
            budget = budget.trim();
        }
        if (userQuestion != null) {
            userQuestion = userQuestion.trim();
        }
    }
}