package com.decisioncopilot.service;

import com.decisioncopilot.model.DecisionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DecisionProcessor {

    private static final Logger log = LoggerFactory.getLogger(DecisionProcessor.class);

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
            persistenceService.persist(queryId, productName, category, budget, userQuestion, startTime);
            statusService.updateStatus(queryId, DecisionStatus.COMPLETED);
            log.info("Background decision processing completed for queryId={} in {}ms",
                queryId, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Background decision processing failed for queryId={}", queryId, e);
            statusService.updateStatusWithError(queryId, DecisionStatus.FAILED, e.getMessage());
        }
    }
}
