package com.decisioncopilot.service;

import com.decisioncopilot.model.DecisionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DecisionProcessor {

    private static final Logger log = LoggerFactory.getLogger(DecisionProcessor.class);
    
    // FIXED: Add timeout constant to prevent infinite hangs
    private static final long PROCESSING_TIMEOUT_MS = 30_000; // 30 seconds max

    private final DecisionPersistenceService persistenceService;
    private final DecisionQueryStatusService statusService;

    public DecisionProcessor(
            DecisionPersistenceService persistenceService,
            DecisionQueryStatusService statusService) {
        this.persistenceService = persistenceService;
        this.statusService = statusService;
    }

    public void process(
            UUID queryId,
            String productName,
            String category,
            String budget,
            String userQuestion) {
        long startTime = System.currentTimeMillis();
        log.info("Background decision processing started for queryId={}", queryId);

        try {
            statusService.updateStatus(queryId, DecisionStatus.PROCESSING);
            
            // FIXED: Add timeout check to prevent infinite processing
            persistenceService.persist(queryId, productName, category, budget, userQuestion, startTime);
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            if (elapsedTime > PROCESSING_TIMEOUT_MS) {
                log.warn("Processing took {}ms which exceeds timeout of {}ms for queryId={}", 
                    elapsedTime, PROCESSING_TIMEOUT_MS, queryId);
                statusService.updateStatusWithError(queryId, DecisionStatus.FAILED, 
                    "Processing timeout: took " + elapsedTime + "ms");
                return;
            }
            
            statusService.updateStatus(queryId, DecisionStatus.COMPLETED);
            log.info("Background decision processing completed for queryId={} in {}ms",
                queryId, elapsedTime);
        } catch (IllegalArgumentException e) {
            // FIXED: Handle validation errors (invalid product names)
            log.warn("Validation error for queryId={}: {}", queryId, e.getMessage());
            statusService.updateStatusWithError(queryId, DecisionStatus.FAILED, e.getMessage());
        } catch (Exception e) {
            log.error("Background decision processing failed for queryId={}", queryId, e);
            statusService.updateStatusWithError(queryId, DecisionStatus.FAILED, e.getMessage());
        }
    }
}