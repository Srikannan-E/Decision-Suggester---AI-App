package com.decisioncopilot.dto;

import jakarta.validation.constraints.NotBlank;

public record DecisionRequest(
    @NotBlank String input,
    @NotBlank String category
) {}