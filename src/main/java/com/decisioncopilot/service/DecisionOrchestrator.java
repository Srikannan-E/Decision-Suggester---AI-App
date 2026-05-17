package com.decisioncopilot.service;

import com.decisioncopilot.dto.DecisionRequest;
import com.decisioncopilot.model.DecisionQuery;
import com.decisioncopilot.model.DecisionStatus;
import com.decisioncopilot.repository.DecisionQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class DecisionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DecisionOrchestrator.class);

    private final DecisionQueryRepository queryRepository;
    private final DecisionProcessor decisionProcessor;
    private final ProductDataServiceImpl productDataService;
    private final Executor llmTaskExecutor;

    public DecisionOrchestrator(
            DecisionQueryRepository queryRepository,
            DecisionProcessor decisionProcessor,
            ProductDataServiceImpl productDataService,
            @Qualifier("llmTaskExecutor") Executor llmTaskExecutor) {
        this.queryRepository = queryRepository;
        this.decisionProcessor = decisionProcessor;
        this.productDataService = productDataService;
        this.llmTaskExecutor = llmTaskExecutor;
    }

    @Transactional
    public UUID submitDecision(DecisionRequest request) {
        String productName = request.input().trim();
        String category = request.category().trim();
        
        // FIXED: Validate product input EARLY before creating query
        // This prevents invalid requests from clogging the processing queue
        try {
            productDataService.fetchProductData(productName, category);
        } catch (IllegalArgumentException e) {
            // Re-throw validation error so controller can return 400
            throw e;
        }
        
        String storedInput = buildStoredInput(productName, category, request.budget(), request.userQuestion());

        DecisionQuery query = new DecisionQuery();
        query.setInputText(storedInput);
        query.setCategory(category);
        query.setStatus(DecisionStatus.PENDING);
        query.setCreatedAt(LocalDateTime.now());
        query = queryRepository.saveAndFlush(query);
        UUID queryId = query.getId();

        log.info("Decision query created with queryId={}, product={}", queryId, productName);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                llmTaskExecutor.execute(() -> {
                    try {
                        log.debug("Starting background processing for queryId={}", queryId);
                        decisionProcessor.process(
                            queryId, productName, category, request.budget(), request.userQuestion());
                    } catch (Exception e) {
                        log.error("Unhandled error running decision task for queryId={}", queryId, e);
                    }
                });
            }
        });

        return queryId;
    }

    private static String buildStoredInput(String product, String category, String budget, String userQuestion) {
        StringBuilder b = new StringBuilder();
        b.append("Product: ").append(product).append("\nCategory: ").append(category);
        if (budget != null && !budget.isBlank()) {
            b.append("\nBudget: ").append(budget);
        }
        if (userQuestion != null && !userQuestion.isBlank()) {
            b.append("\nQuestion: ").append(userQuestion);
        }
        return b.toString();
    }
}