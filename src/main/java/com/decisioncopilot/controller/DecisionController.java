package com.decisioncopilot.controller;

import com.decisioncopilot.dto.DecisionHistoryResponse;
import com.decisioncopilot.dto.DecisionRequest;
import com.decisioncopilot.dto.DecisionResponse;
import com.decisioncopilot.dto.ErrorResponse;
import com.decisioncopilot.service.DecisionOrchestrator;
import com.decisioncopilot.service.DecisionQueryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/decisions")
public class DecisionController {

    private static final Logger log = LoggerFactory.getLogger(DecisionController.class);
    private final DecisionOrchestrator orchestrator;
    private final DecisionQueryService queryService;

    public DecisionController(DecisionOrchestrator orchestrator, DecisionQueryService queryService) {
        this.orchestrator = orchestrator;
        this.queryService = queryService;
    }

    /**
     * FIXED: Submit a new product decision request with validation
     * Returns error 400 if input is not a valid product
     */
    @PostMapping
    public ResponseEntity<?> submitDecision(@Valid @RequestBody DecisionRequest request) {
        try {
            // Input validation happens in orchestrator/service layer
            UUID queryId = orchestrator.submitDecision(request);

            return ResponseEntity
                .accepted()
                .location(URI.create("/api/decisions/" + queryId))
                .body(Map.of(
                    "id", queryId,
                    "status", "PENDING",
                    "message", "Decision is being processed. Poll the status endpoint.",
                    "pollUrl", "/api/decisions/" + queryId
                ));
        } catch (IllegalArgumentException e) {
            // FIXED: Return 400 Bad Request for invalid product inputs
            log.warn("Invalid product input: {}", e.getMessage());
            return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(
                    "INVALID_INPUT",
                    e.getMessage(),
                    "Please provide a valid product name (e.g., 'iPhone 15', 'Samsung Galaxy S24')"
                ));
        }
    }

    /**
     * FIXED: Poll for decision status - returns ONLY the specific query result
     * No appending of other history results
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDecision(@PathVariable UUID id) {
        try {
            // CRITICAL FIX: Get ONLY this specific decision, not history
            // Prevents appending other results to current query
            DecisionResponse response = queryService.getDecisionById(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching decision {}", id, e);
            return ResponseEntity
                .notFound()
                .build();
        }
    }

    /**
     * Browse paginated decision history (separate endpoint)
     * FIXED: History should be fetched separately, not appended to active query
     */
    @GetMapping
    public ResponseEntity<DecisionHistoryResponse> getDecisionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var history = queryService.getDecisionHistory(page, size);
        return ResponseEntity.ok(history);
    }
}