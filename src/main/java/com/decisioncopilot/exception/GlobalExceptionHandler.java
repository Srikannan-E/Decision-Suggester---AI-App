package com.decisioncopilot.exception;

import com.decisioncopilot.dto.ErrorResponse;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * FIXED: Updated to match new ErrorResponse signature (String code, String message, String hint)
     * Handle Bean Validation failures (400 Bad Request)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));

        ErrorResponse response = new ErrorResponse(
            "VALIDATION_ERROR",
            "Validation Failed",
            details.isEmpty() ? "Request contains invalid fields" : details
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * FIXED: Updated to match new ErrorResponse signature
     * Handle not-found exceptions (404 Not Found)
     */
    @ExceptionHandler({EntityNotFoundException.class, NoSuchElementException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex) {
        ErrorResponse response = new ErrorResponse(
            "NOT_FOUND",
            "Not Found",
            ex.getMessage() != null ? ex.getMessage() : "The requested resource does not exist"
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * FIXED: Updated to match new ErrorResponse signature
     * Handle rate limiter rejection (429 Too Many Requests)
     */
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RequestNotPermitted ex) {
        ErrorResponse response = new ErrorResponse(
            "RATE_LIMIT_EXCEEDED",
            "Rate Limit Exceeded",
            "Too many requests. Please try again later."
        );

        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "60");

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .headers(headers)
            .body(response);
    }

    /**
     * FIXED: Updated to match new ErrorResponse signature
     * Handle IllegalArgumentException for invalid product input
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
            "INVALID_INPUT",
            "Invalid Product Input",
            ex.getMessage() != null ? ex.getMessage() : "Please provide a valid product name"
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * FIXED: Updated to match new ErrorResponse signature
     * Catch-all for unexpected errors (500 Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        ErrorResponse response = new ErrorResponse(
            "INTERNAL_SERVER_ERROR",
            "Internal Server Error",
            "An unexpected error occurred. Please try again later."
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}