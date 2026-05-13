package com.decisioncopilot.service;

import com.decisioncopilot.dto.ProductData;
import com.decisioncopilot.dto.LlmDecisionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class LlmDecisionService {

    private static final Logger log = LoggerFactory.getLogger(LlmDecisionService.class);
    // private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LlmDecisionService(/*ChatClient.Builder chatClientBuilder, */ObjectMapper objectMapper) {
        // this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = "llmService", fallbackMethod = "fallbackDecision")
    @Retry(name = "llmService")
    public LlmDecisionResult generateDecision(ProductData product) {
        log.info("Generating decision for product: {}", product.name());

        // Mock response for now
        String response = """
            {
                "verdict": "BUY",
                "confidenceScore": 0.85,
                "pros": ["Good value", "High rating"],
                "cons": ["Some reviews mention issues"],
                "summary": "Overall a good purchase",
                "reasoning": "Based on price and rating"
            }
            """;

        return parseResponse(response);
    }

    private String buildPrompt(ProductData product) {
        return """
            You are a product purchase advisor. Analyze the following product and provide a buying recommendation.
            
            Product: %s
            Price: $%s
            Rating: %s/5.0 (%d reviews)
            Category: %s
            
            Respond in the following JSON format only, no markdown:
            {
              "verdict": "BUY" or "DONT_BUY" or "MAYBE",
              "confidenceScore": 0.0 to 1.0,
              "pros": ["pro1", "pro2", "pro3"],
              "cons": ["con1", "con2", "con3"],
              "summary": "One paragraph summary of your recommendation",
              "reasoning": "Detailed reasoning for your verdict"
            }
            """.formatted(
                product.name(),
                product.price(),
                product.rating(),
                product.reviewCount(),
                product.category()
            );
    }

    private LlmDecisionResult parseResponse(String response) {
        try {
            return objectMapper.readValue(response, LlmDecisionResult.class);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", response, e);
            throw new RuntimeException("Failed to parse LLM response", e);
        }
    }

    private LlmDecisionResult fallbackDecision(ProductData product, Throwable t) {
        log.warn("Circuit breaker triggered for product: {}. Error: {}", product.name(), t.getMessage());
        return new LlmDecisionResult(
            "MAYBE",
            BigDecimal.valueOf(0.5),
            new String[]{"Unable to fully analyze at this time"},
            new String[]{"AI service temporarily unavailable"},
            "The AI decision service is temporarily unavailable. Please try again later.",
            "Fallback response due to service disruption: " + t.getMessage()
        );
    }
}
