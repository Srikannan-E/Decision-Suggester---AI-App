package com.decisioncopilot.service;

import com.decisioncopilot.dto.LlmDecisionResult;
import com.decisioncopilot.dto.ProductData;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Gemini LLM Service - Uses native Java HttpURLConnection
 * Generates intelligent product decisions using Google Gemini API
 * Runs asynchronously in background thread (non-blocking)
 * 
 * API Docs: https://ai.google.dev/tutorials/rest_quickstart
 */
@Service
public class LlmDecisionService {

    private static final Logger log = LoggerFactory.getLogger(LlmDecisionService.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    
    @Value("${spring.gemini.api-key:}")
    private String apiKey;
    
    private final Gson gson = new Gson();

    public LlmDecisionService() {
        // Empty constructor - Spring manages bean creation
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

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key not configured, using fallback decision");
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
        
        prompt.append("\nRESPOND with JSON only (no markdown, no extra text):\n");
        prompt.append("{\n");
        prompt.append("  \"verdict\": \"BUY\"|\"MAYBE\"|\"DONT_BUY\",\n");
        prompt.append("  \"confidenceScore\": 0.45,\n");
        prompt.append("  \"pros\": [\"pro1\", \"pro2\", \"pro3\", \"pro4\"],\n");
        prompt.append("  \"cons\": [\"con1\", \"con2\", \"con3\", \"con4\", \"con5\"],\n");
        prompt.append("  \"summary\": \"1-2 line summary\",\n");
        prompt.append("  \"reasoning\": \"detailed reasoning\"\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }

    /**
     * Call Gemini API using native Java HttpURLConnection
     * No external HTTP client library needed
     */
    @Retryable(
        retryFor = { Exception.class },
        maxAttempts = 2,
        backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    private String callGeminiApi(String prompt) throws Exception {
        log.debug("Calling Gemini API...");
        
        String urlString = GEMINI_API_URL + "?key=" + apiKey;
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            
            // Build request JSON
            JsonObject requestBody = new JsonObject();
            JsonArray contentsArray = new JsonArray();
            JsonObject content = new JsonObject();
            JsonArray partsArray = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", prompt);
            partsArray.add(part);
            content.add("parts", partsArray);
            contentsArray.add(content);
            requestBody.add("contents", contentsArray);
            
            String requestJson = gson.toJson(requestBody);
            log.debug("Gemini request: {}", requestJson);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Read response
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                String errorMessage = new String(connection.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                log.error("Gemini API error ({}): {}", responseCode, errorMessage);
                throw new RuntimeException("Gemini API error: " + responseCode + " - " + errorMessage);
            }
            
            String response = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.debug("Gemini response: {} chars", response.length());
            
            return response;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Parse JSON response from Gemini API
     */
    private LlmDecisionResult parseGeminiResponse(String response, ProductData product) {
        try {
            JsonObject responseObj = JsonParser.parseString(response).getAsJsonObject();
            
            // Extract text from candidates
            if (!responseObj.has("candidates") || responseObj.getAsJsonArray("candidates").size() == 0) {
                log.warn("No candidates in Gemini response");
                return fallbackDecision(product);
            }
            
            JsonObject candidate = responseObj.getAsJsonArray("candidates").get(0).getAsJsonObject();
            JsonObject contentObj = candidate.getAsJsonObject("content");
            JsonArray parts = contentObj.getAsJsonArray("parts");
            String textResponse = parts.get(0).getAsJsonObject().get("text").getAsString();
            
            log.debug("Gemini text response: {}", textResponse);
            
            // Parse the JSON from the text
            JsonObject decisionJson = parseJsonFromText(textResponse);
            
            String verdict = getStringOrDefault(decisionJson, "verdict", "MAYBE");
            if (!isValidVerdict(verdict)) {
                verdict = "MAYBE";
            }
            
            BigDecimal confidence = getConfidenceOrDefault(decisionJson, "confidenceScore", BigDecimal.valueOf(0.65));
            
            String[] pros = getStringArrayOrDefault(decisionJson, "pros", 4);
            String[] cons = getStringArrayOrDefault(decisionJson, "cons", 5);
            
            String summary = getStringOrDefault(decisionJson, "summary", "Product analysis based on available data.");
            String reasoning = getStringOrDefault(decisionJson, "reasoning", "Decision based on rating, price, and review analysis.");
            
            return new LlmDecisionResult(verdict, confidence, pros, cons, summary, reasoning);
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage(), e);
            return fallbackDecision(product);
        }
    }

    /**
     * Extract JSON object from text response (handles markdown code blocks)
     */
    private JsonObject parseJsonFromText(String text) {
        try {
            // Remove markdown code block markers if present
            String cleanText = text.replace("```json\n", "")
                                   .replace("```json", "")
                                   .replace("```\n", "")
                                   .replace("```", "")
                                   .trim();
            
            return JsonParser.parseString(cleanText).getAsJsonObject();
        } catch (Exception e) {
            log.warn("Failed to parse JSON from text: {}", text);
            return new JsonObject();
        }
    }

    /**
     * Get string value from JSON with default
     */
    private String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsString();
            }
        } catch (Exception e) {
            log.debug("Failed to get string value for key: {}", key);
        }
        return defaultValue;
    }

    /**
     * Get confidence score from JSON with bounds checking
     */
    private BigDecimal getConfidenceOrDefault(JsonObject obj, String key, BigDecimal defaultValue) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                double value = obj.get(key).getAsDouble();
                BigDecimal bd = BigDecimal.valueOf(value);
                if (bd.compareTo(BigDecimal.valueOf(0.38)) < 0) return BigDecimal.valueOf(0.38);
                if (bd.compareTo(BigDecimal.valueOf(0.93)) > 0) return BigDecimal.valueOf(0.93);
                return bd.setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.debug("Failed to parse confidence score");
        }
        return defaultValue;
    }

    /**
     * Get string array from JSON
     */
    private String[] getStringArrayOrDefault(JsonObject obj, String key, int maxSize) {
        try {
            if (obj.has(key) && obj.get(key).isJsonArray()) {
                JsonArray array = obj.getAsJsonArray(key);
                String[] result = new String[Math.min(array.size(), maxSize)];
                for (int i = 0; i < result.length; i++) {
                    result[i] = array.get(i).getAsString();
                }
                return result;
            }
        } catch (Exception e) {
            log.debug("Failed to parse array for key: {}", key);
        }
        return new String[0];
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