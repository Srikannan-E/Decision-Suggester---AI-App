package com.decisioncopilot.service;

import com.decisioncopilot.dto.DecisionHistoryResponse;
import com.decisioncopilot.dto.DecisionResponse;
import com.decisioncopilot.model.DecisionQuery;
import com.decisioncopilot.model.DecisionResult;
import com.decisioncopilot.model.Product;
import com.decisioncopilot.repository.DecisionQueryRepository;
import com.decisioncopilot.repository.DecisionResultRepository;
import com.decisioncopilot.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DecisionQueryService {

    private final DecisionQueryRepository queryRepository;
    private final DecisionResultRepository resultRepository;
    private final ProductRepository productRepository;

    public DecisionQueryService(DecisionQueryRepository queryRepository,
                                DecisionResultRepository resultRepository,
                                ProductRepository productRepository) {
        this.queryRepository = queryRepository;
        this.resultRepository = resultRepository;
        this.productRepository = productRepository;
    }

    // Fetch a single decision by query ID and map to response DTO
    public DecisionResponse getDecisionById(UUID id) {
        DecisionQuery query = queryRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Decision not found: " + id));

        DecisionResult result = resultRepository.findByQueryId(id).orElse(null);
        Product product = result != null ? productRepository.findById(result.getProductId()).orElse(null) : null;

        return mapToResponse(query, result, product);
    }

    // Fetch paginated decision history
    public DecisionHistoryResponse getDecisionHistory(int page, int size) {
        Page<DecisionResponse> history = queryRepository.findAll(PageRequest.of(page, size))
            .map(query -> {
                DecisionResult result = resultRepository.findByQueryId(query.getId()).orElse(null);
                Product product = result != null ? productRepository.findById(result.getProductId()).orElse(null) : null;
                return mapToResponse(query, result, product);
            });

        return new DecisionHistoryResponse(
            history.getContent(),
            history.getNumber(),
            history.getSize(),
            history.getTotalElements(),
            history.getTotalPages()
        );
    }

    // Map entity pair to the response DTO
    private DecisionResponse mapToResponse(DecisionQuery query, DecisionResult result, Product product) {
        if (result == null || product == null) {
            return new DecisionResponse(
                query.getId(),
                query.getStatus().name(),
                null, null, null, null, null, null, null, null, null, null, null, null,
                query.getCreatedAt(),
                query.getErrorMessage()
            );
        }

        return new DecisionResponse(
            query.getId(),
            query.getStatus().name(),
            product.getName(),
            product.getPrice(),
            product.getRating(),
            result.getVerdict(),
            result.getConfidenceScore(),
            result.getPros(),
            result.getCons(),
            product.getFeatureHighlights(),
            product.getSpecSummary(),
            result.getSummary(),
            result.getReasoning(),
            result.getProcessingTimeMs(),
            query.getCreatedAt(),
            null
        );
    }
}
