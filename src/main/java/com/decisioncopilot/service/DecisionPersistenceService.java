package com.decisioncopilot.service;

import com.decisioncopilot.dto.LlmDecisionResult;
import com.decisioncopilot.dto.ProductData;
import com.decisioncopilot.model.DecisionResult;
import com.decisioncopilot.model.Product;
import com.decisioncopilot.repository.DecisionResultRepository;
import com.decisioncopilot.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DecisionPersistenceService {

    private final ProductDataService productDataService;
    private final LlmDecisionService llmDecisionService;
    private final DecisionResultRepository resultRepository;
    private final ProductRepository productRepository;
    private final DecisionQueryStatusService statusService;

    public DecisionPersistenceService(
            ProductDataService productDataService,
            LlmDecisionService llmDecisionService,
            DecisionResultRepository resultRepository,
            ProductRepository productRepository,
            DecisionQueryStatusService statusService) {
        this.productDataService = productDataService;
        this.llmDecisionService = llmDecisionService;
        this.resultRepository = resultRepository;
        this.productRepository = productRepository;
        this.statusService = statusService;
    }

    @Transactional
    public void persist(
            UUID queryId,
            String productName,
            String category,
            String budget,
            String userQuestion,
            long startTime) {
        ProductData productData = withBuyerContext(
            productDataService.fetchProductData(productName, category),
            budget,
            userQuestion);

        Product product = saveProduct(productData);
        statusService.linkProduct(queryId, product.getId());

        LlmDecisionResult llmResult = llmDecisionService.generateDecision(productData);
        long processingTime = System.currentTimeMillis() - startTime;
        saveDecisionResult(queryId, product.getId(), llmResult, processingTime);
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
        resultRepository.saveAndFlush(result);
    }
}
