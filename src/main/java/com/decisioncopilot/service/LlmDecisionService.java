package com.decisioncopilot.service;

import com.decisioncopilot.dto.LlmDecisionResult;
import com.decisioncopilot.dto.ProductData;
import com.google.generativeai.client.GenerativeAI;
import com.google.generativeai.types.generativedatetime.GenerateContentResponse;
import com.google.generativeai.types.generativedatetime.GenerativeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * UPDATED: Gemini LLM Service with Caching & Retry
 * Generates intelligent product decisions using Google Gemini API
 * Runs asynchronously in background thread (non-blocking)
 */
@Service
public class LlmDecisionService {

    private static final Logger log = LoggerFactory.getLogger(LlmDecisionService.class);

    private final GenerativeAI generativeAI;
    
    @Value("${spring.gemini.model-name:gemini-2.5-flash}")
    private String modelName;
    
    @Value("${spring.gemini.api-key:}")
    private String apiKey;

    public LlmDecisionService(@Value("${spring.gemini.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY not configured. Fallback mode enabled.");
            this.generativeAI = null;
        } else {
            this.generativeAI = new GenerativeAI(apiKey);
            log.info("Gemini AI initialized successfully");
        }
    }

    /**
     * Generate decision using Gemini API
     * Cached to avoid redundant calls for same product
     * Retryable with exponential backoff
     */
    @Cacheable(value = "geminiResponses", key = "#product.name + '_' + #product.category")
    @Retryable(
        retryFor = { Exception.class },
        maxAttempts = 2,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public LlmDecisionResult generateDecision(ProductData product) {
        log.info("Generating Gemini decision for product: {} in category: {}", 
            product.name(), product.category());

        if (generativeAI == null) {
            log.warn("Gemini API not configured, using fallback decision");
            return fallbackDecision(product);
        }

        try {
            String prompt = buildPrompt(product);
            String response = callGeminiApi(prompt);
            LlmDecisionResult result = parseGeminiResponse(response, product);
            
            log.debug("Gemini decision generated: verdict={}, confidence={}", 
                result.verdict(), result.confidenceScore());
            
            return result;
        } catch (Exception e) {
            log.error("Error generating Gemini decision for product: {}", product.name(), e);
            return fallbackDecision(product);
        }
    }

    /**
     * Build structured prompt for Gemini
     */
    private String buildPrompt(ProductData product) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Analyze this product and provide a buying decision in JSON format.\n\n");
        prompt.append("PRODUCT:\n");
        prompt.append("Name: ").append(product.name()).append("\n");
        prompt.append("Category: ").append(product.category()).append("\n");
        prompt.append("Price: ₹").append(product.price()).append("\n");
        prompt.append("Rating: ").append(product.rating()).append("/5.0\n");
        prompt.append("Reviews: ").append(product.reviewCount()).append("\n");
        
        if (product.featureHighlights() != null && product.featureHighlights().length > 0) {
            prompt.append("Features: ");
            for (int i = 0; i < product.featureHighlights().length; i++) {
                if (i > 0) prompt.append(", ");
                prompt.append(product.featureHighlights()[i]);
            }
            prompt.append("\n");
        }
        
        if (product.buyerBudget() != null && !product.buyerBudget().isBlank()) {
            prompt.append("Budget: ").append(product.buyerBudget()).append("\n");
        }
        
        if (product.buyerQuestion() != null && !product.buyerQuestion().isBlank()) {
            prompt.append("Question: ").append(product.buyerQuestion()).append("\n");
        }
        
        prompt.append("\nRESPOND with JSON only (no extra text):\n");
        prompt.append("{\n");
        prompt.append("  \"verdict\": \"BUY\"|\"MAYBE\"|\"DONT_BUY\",\n");
        prompt.append("  \"confidenceScore\": 0.38-0.93,\n");
        prompt.append("  \"pros\": [\"pro1\", \"pro2\", \"pro3\", \"pro4\"],\n");
        prompt.append("  \"cons\": [\"con1\", \"con2\", \"con3\", \"con4\", \"con5\"],\n");
        prompt.append("  \"summary\": \"1-2 line summary\",\n");
        prompt.append("  \"reasoning\": \"detailed reasoning\"\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }

    /**
     * Call Gemini API with timeout
     */
    @Retryable(
        retryFor = { Exception.class },
        maxAttempts = 2,
        backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    private String callGeminiApi(String prompt) {
        try {
            GenerativeModel model = generativeAI.getGenerativeModel(modelName);
            GenerateContentResponse response = model.generateContent(prompt);
            String result = response.getText();
            
            log.debug("Gemini API response: {} chars", result.length());
            return result;
        } catch (Exception e) {
            log.error("Gemini API failed", e);
            throw new RuntimeException("Gemini API error: " + e.getMessage(), e);
        }
    }

    /**
     * Parse JSON response from Gemini
     */
    private LlmDecisionResult parseGeminiResponse(String response, ProductData product) {
        try {
            String verdict = extractJsonValue(response, "verdict");
            if (!isValidVerdict(verdict)) {
                verdict = "MAYBE";
            }
            
            String confidenceStr = extractJsonValue(response, "confidenceScore");
            BigDecimal confidence = parseConfidence(confidenceStr);
            
            String[] pros = parseJsonArray(response, "pros", 4);
            String[] cons = parseJsonArray(response, "cons", 5);
            
            String summary = extractJsonValue(response, "summary");
            if (summary == null || summary.isBlank()) {
                summary = "Product analysis based on available data.";
            }
            
            String reasoning = extractJsonValue(response, "reasoning");
            if (reasoning == null || reasoning.isBlank()) {
                reasoning = "Decision based on rating, price, and review analysis.";
            }
            
            return new LlmDecisionResult(verdict, confidence, pros, cons, summary, reasoning);
        } catch (Exception e) {
            log.warn("JSON parsing failed, using fallback", e);
            return fallbackDecision(product);
        }
    }

    /**
     * Extract value from JSON string
     */
    private String extractJsonValue(String json, String key) {
        try {
            int keyIdx = json.indexOf("\"" + key + "\"");
            if (keyIdx == -1) return null;
            
            int colonIdx = json.indexOf(":", keyIdx);
            if (colonIdx == -1) return null;
            
            int startIdx = colonIdx + 1;
            while (startIdx < json.length() && Character.isWhitespace(json.charAt(startIdx))) {
                startIdx++;
            }
            
            char firstChar = json.charAt(startIdx);
            int endIdx;
            
            if (firstChar == '"') {
                endIdx = json.indexOf("\"", startIdx + 1);
                return json.substring(startIdx + 1, endIdx);
            } else if (firstChar == '[') {
                endIdx = json.indexOf("]", startIdx) + 1;
                return json.substring(startIdx, endIdx);
            } else {
                endIdx = startIdx;
                while (endIdx < json.length() && 
                       !Character.isWhitespace(json.charAt(endIdx)) && 
                       json.charAt(endIdx) != ',' && 
                       json.charAt(endIdx) != '}') {
                    endIdx++;
                }
                return json.substring(startIdx, endIdx).trim();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse JSON array from response
     */
    private String[] parseJsonArray(String json, String key, int maxSize) {
        try {
            String arrayStr = extractJsonValue(json, key);
            if (arrayStr == null || !arrayStr.startsWith("[")) return new String[0];
            
            String content = arrayStr.substring(1, arrayStr.length() - 1);
            String[] items = content.split(",");
            
            java.util.List<String> result = new java.util.ArrayList<>();
            for (String item : items) {
                item = item.trim();
                if (item.startsWith("\"") && item.endsWith("\"")) {
                    result.add(item.substring(1, item.length() - 1));
                }
                if (result.size() >= maxSize) break;
            }
            
            return result.toArray(new String[0]);
        } catch (Exception e) {
            return new String[0];
        }
    }

    /**
     * Parse and validate confidence score
     */
    private BigDecimal parseConfidence(String value) {
        try {
            if (value == null || value.isBlank()) return BigDecimal.valueOf(0.65);
            BigDecimal bd = new BigDecimal(value.trim());
            if (bd.compareTo(BigDecimal.valueOf(0.38)) < 0) return BigDecimal.valueOf(0.38);
            if (bd.compareTo(BigDecimal.valueOf(0.93)) > 0) return BigDecimal.valueOf(0.93);
            return bd.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.valueOf(0.65);
        }
    }

    /**
     * Validate verdict value
     */
    private boolean isValidVerdict(String verdict) {
        return verdict != null && (
            verdict.equalsIgnoreCase("BUY") ||
            verdict.equalsIgnoreCase("MAYBE") ||
            verdict.equalsIgnoreCase("DONT_BUY")
        );
    }

    /**
     * Fallback when Gemini API fails
     */
    private LlmDecisionResult fallbackDecision(ProductData product) {
        log.warn("Using fallback decision for: {}", product.name());
        return new LlmDecisionResult(
            "MAYBE",
            BigDecimal.valueOf(0.65),
            new String[]{"Review mixed signals", "Compare alternatives", "Check seller ratings", "Verify specs"},
            new String[]{"AI unavailable", "Validate details", "Check availability", "Compare prices", "Check warranty"},
            "Unable to generate AI recommendation - analyze product details manually.",
            "Fallback analysis: Please retry or make decision based on available information."
        );
    }
}