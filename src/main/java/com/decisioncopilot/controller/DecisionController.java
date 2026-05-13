package com.decisioncopilot.controller;

import com.decisioncopilot.dto.DecisionHistoryResponse;
import com.decisioncopilot.dto.DecisionRequest;
import com.decisioncopilot.dto.DecisionResponse;
import com.decisioncopilot.service.DecisionOrchestrator;
import com.decisioncopilot.service.DecisionQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/decisions")
public class DecisionController {

    private final DecisionOrchestrator orchestrator;
    private final DecisionQueryService queryService;

    public DecisionController(DecisionOrchestrator orchestrator, DecisionQueryService queryService) {
        this.orchestrator = orchestrator;
        this.queryService = queryService;
    }

    // Submit a new product decision request
    @PostMapping
    // @RateLimiter(name = "decisionApi")
    public ResponseEntity<Map<String, Object>> submitDecision(@Valid @RequestBody DecisionRequest request) {
        UUID queryId = orchestrator.submitDecision(request.input(), request.category());

        return ResponseEntity
            .accepted()
            .location(URI.create("/api/decisions/" + queryId))
            .body(Map.of(
                "id", queryId,
                "status", "PENDING",
                "message", "Decision is being processed. Poll the status endpoint.",
                "pollUrl", "/api/decisions/" + queryId
            ));
    }

    // Poll for decision status and result
    @GetMapping("/{id}")
    public ResponseEntity<DecisionResponse> getDecision(@PathVariable UUID id) {
        DecisionResponse response = queryService.getDecisionById(id);
        return ResponseEntity.ok(response);
    }

    // Browse paginated decision history
    @GetMapping
    public ResponseEntity<DecisionHistoryResponse> getDecisionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var history = queryService.getDecisionHistory(page, size);
        return ResponseEntity.ok(history);
    }
}
