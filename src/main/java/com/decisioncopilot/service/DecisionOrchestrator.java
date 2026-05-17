package com.decisioncopilot.service;

import com.decisioncopilot.dto.DecisionRequest;
import com.decisioncopilot.dto.LlmDecisionResult;
import com.decisioncopilot.dto.ProductData;
import com.decisioncopilot.model.DecisionQuery;
import com.decisioncopilot.model.DecisionResult;
import com.decisioncopilot.model.DecisionStatus;
import com.decisioncopilot.model.Product;
import com.decisioncopilot.repository.DecisionQueryRepository;
import com.decisioncopilot.repository.DecisionResultRepository;
import com.decisioncopilot.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class DecisionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DecisionOrchestrator.class);
    private final ProductDataService productDataService;
    private final LlmDecisionService llmDecisionService;
    private final DecisionQueryRepository queryRepository;
    private final DecisionResultRepository resultRepository;
    private final ProductRepository productRepository;
    private final DecisionOrchestrator self;

    public DecisionOrchestrator(
            ProductDataService productDataService,
            LlmDecisionService llmDecisionService,
            DecisionQueryRepository queryRepository,
            DecisionResultRepository resultRepository,
            ProductRepository productRepository,
            @Lazy DecisionOrchestrator self) {
        this.productDataService = productDataService;
        this.llmDecisionService = llmDecisionService;
        this.queryRepository = queryRepository;
        this.resultRepository = resultRepository;
        this.productRepository = productRepository;
        this.self = self;
    }

        // Entry point: creates a PENDING query and kicks off background processing
    @Transactional
    public UUID submitDecision(DecisionRequest request) {
        String productName = request.input().trim();
        String category = request.category().trim();
        String storedInput = buildStoredInput(productName, category, request.budget(), request.userQuestion());

        DecisionQuery query = new DecisionQuery();
        query.setInputText(storedInput);
        query.setCategory(category);
        query.setStatus(DecisionStatus.PENDING);
        query.setCreatedAt(LocalDateTime.now());
        query = queryRepository.save(query);
        UUID queryId = query.getId();

        // Start background work only after this transaction commits so the row is visible
        // to the async thread and status updates are not blocked on the insert lock.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                self.processDecisionAsync(queryId, productName, category, request.budget(), request.userQuestion());
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

    private static ProductData withBuyerContext(ProductData base, String budget, String userQuestion) {
        String b = (budget == null || budget.isBlank()) ? null : budget;
        String q = (userQuestion == null || userQuestion.isBlank()) ? null : userQuestion;
        if (b == null && q == null) {
            return base;
        }
        return new ProductData(
            base.name(),
            base.price(),
            base.rating(),
            base.reviewCount(),
            base.category(),
            b,
            q,
            base.featureHighlights(),
            base.specSummary());
    }

    // Runs on the llmTaskExecutor thread pool, not the request thread
    @Async("llmTaskExecutor")
    @Transactional
    public CompletableFuture<Void> processDecisionAsync(
            UUID queryId,
            String productName,
            String category,
            String budget,
            String userQuestion) {
        long startTime = System.currentTimeMillis();
        log.info("Processing decision async for queryId: {}", queryId);

        try {
            self.updateStatus(queryId, DecisionStatus.PROCESSING);

            ProductData productData = withBuyerContext(
                productDataService.fetchProductData(productName, category),
                budget,
                userQuestion);
            Product product = saveProduct(productData);

            self.linkProductToQuery(queryId, product.getId());

            LlmDecisionResult llmResult = llmDecisionService.generateDecision(productData);

            long processingTime = System.currentTimeMillis() - startTime;
            saveDecisionResult(queryId, product.getId(), llmResult, processingTime);

            self.updateStatus(queryId, DecisionStatus.COMPLETED);
            log.info("Decision completed for queryId: {} in {}ms", queryId, processingTime);

        } catch (Exception e) {
            log.error("Decision processing failed for queryId: {}", queryId, e);
            self.updateStatusWithError(queryId, DecisionStatus.FAILED, e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    // Cached lookup: Redis stores the result for 30 minutes
    @Cacheable(value = "decisions", key = "#queryId")
    public DecisionResult getDecisionResult(UUID queryId) {
        return resultRepository.findByQueryId(queryId).orElse(null);
    }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(UUID queryId, DecisionStatus status) {
        queryRepository.updateStatus(queryId, status);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatusWithError(UUID queryId, DecisionStatus status, String error) {
        queryRepository.updateStatusWithError(queryId, status, error);
    }

    private Product saveProduct(ProductData data) {
        Product product = new Product();
        product.setName(data.name());
        product.setPrice(data.price());
        product.setRating(data.rating());
        product.setReviewCount(data.reviewCount());
        product.setCategory(data.category());
        product.setFeatureHighlights(data.featureHighlights());
        product.setSpecSummary(data.specSummary());
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    @Transactional
    public void linkProductToQuery(UUID queryId, UUID productId) {
        queryRepository.linkProduct(queryId, productId);
    }

    private void saveDecisionResult(UUID queryId, UUID productId, LlmDecisionResult llmResult, long processingTime) {
        DecisionResult result = new DecisionResult();
        result.setQueryId(queryId);
        result.setProductId(productId);
        result.setVerdict(llmResult.verdict());
        result.setConfidenceScore(llmResult.confidenceScore());
        result.setPros(llmResult.pros());
        result.setCons(llmResult.cons());
        result.setSummary(llmResult.summary());
        result.setReasoning(llmResult.reasoning());
        result.setLlmModel("gemini-2.5-flash");
        result.setProcessingTimeMs(processingTime);
        result.setCreatedAt(LocalDateTime.now());
        resultRepository.save(result);
    }
}
