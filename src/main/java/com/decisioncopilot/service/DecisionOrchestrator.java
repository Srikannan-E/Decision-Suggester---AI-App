package com.decisioncopilot.service;

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
    public UUID submitDecision(String input, String category) {
        DecisionQuery query = new DecisionQuery();
        query.setInputText(input);
        query.setCategory(category);
        query.setStatus(DecisionStatus.PENDING);
        query.setCreatedAt(LocalDateTime.now());
        query = queryRepository.save(query);

        self.processDecisionAsync(query.getId(), input, category);

        return query.getId();
    }

    // Runs on the llmTaskExecutor thread pool, not the request thread
    @Async("llmTaskExecutor")
    public CompletableFuture<Void> processDecisionAsync(UUID queryId, String input, String category) {
        long startTime = System.currentTimeMillis();
        log.info("Processing decision async for queryId: {}", queryId);

        try {
            self.updateStatus(queryId, DecisionStatus.PROCESSING);

            ProductData productData = productDataService.fetchProductData(input, category);
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
