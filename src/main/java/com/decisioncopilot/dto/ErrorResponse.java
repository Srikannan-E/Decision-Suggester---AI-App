package com.decisioncopilot.dto;

import java.time.LocalDateTime;
import java.util.List;

// Consistent error response shape for all API errors
public record ErrorResponse(
    int status,
    String error,
    String message,
    List<String> details,
    LocalDateTime timestamp
) {
    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, List.of(), LocalDateTime.now());
    }
}
