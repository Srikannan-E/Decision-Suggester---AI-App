package com.decisioncopilot.dto;

import java.time.LocalDateTime;
import java.util.List;

// Consistent error response shape for all API errors
public record ErrorResponse(
    String code,
    String message,
    String hint
) {}
