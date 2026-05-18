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

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
 */
@Service
public class LlmDecisionService {

    private static final Logger log = LoggerFactory.getLogger(LlmDecisionService.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    
    @Value("${spring.gemini.api-key:}")
    private String apiKey;
    
    private final Gson gson = new Gson();

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

        // Check API key at runtime
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) {
            key = apiKey;
        }
        
        if (key == null || key.isBlank()) {
            log.warn("Gemini API key not configured, using fallback decision");
            return fallbackDecision(product);
        }

        try {
            String prompt = buildPrompt(product);
            String response = callGeminiApi(prompt, key);
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
        
        prompt.append("You are an expert product advisor. Analyze this product and provide a buying decision.\n\n");
        prompt.append("PRODUCT DATA:\n");
        prompt.append("Name: ").append(product.name()).append("\n");
        prompt.append("Category: ").append(product.category()).append("\n");
        prompt.append("Price: ₹").append(product.price()).append("\n");
        prompt.append("Rating: ").append(product.rating()).append("/5.0\n");
        prompt.append("Reviews: ").append(product.reviewCount()).append("\n");
        
        if (product.buyerBudget() != null && !product.buyerBudget().isBlank()) {
            prompt.append("Budget: ").append(product.buyerBudget()).append("\n");
        }
        
        if (product.buyerQuestion() != null && !product.buyerQuestion().isBlank()) {
            prompt.append("Buyer Question: ").append(product.buyerQuestion()).append("\n");
        }
        
        prompt.append("\nPROVIDE RESPONSE AS VALID JSON (no markdown, no code blocks):\n");
        prompt.append("{\n");
        prompt.append("  \"verdict\": \"BUY\" or \"MAYBE\" or \"DONT_BUY\",\n");
        prompt.append("  \"confidenceScore\": 0.65,\n");
        prompt.append("  \"pros\": [\"High quality build\", \"Good battery life\", \"Excellent ANC\", \"Premium sound\"],\n");
        prompt.append("  \"cons\": [\"Expensive\", \"Limited customization\", \"Requires Apple ecosystem\", \"No USB-C charging\", \"Repair costs high\"],\n");
        prompt.append("  \"summary\": \"AirPods Pro 3 offer excellent audio quality and ANC but come at premium pricing.\",\n");
        prompt.append("  \"reasoning\": \"With a 4.2/5 rating, the product shows strong market approval. The price point is high but justified by features. Recommended for users deeply invested in Apple ecosystem.\"\n");
        prompt.append("}\n");
        prompt.append("\nIMPORTANT: Return ONLY valid JSON, nothing else.\n");
        
        return prompt.toString();
    }

    /**
     * Call Gemini API using native Java HttpURLConnection
     */
    @Retryable(
        retryFor = { Exception.class },
        maxAttempts = 2,
        backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    private String callGeminiApi(String prompt, String apiKey) throws Exception {
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
            log.debug("Gemini request payload size: {} bytes", requestJson.length());
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Read response
            int responseCode = connection.getResponseCode();
            log.debug("Gemini API response code: {}", responseCode);
            
            if (responseCode != 200) {
                String errorMessage = readStream(connection.getErrorStream());
                log.error("Gemini API error ({}): {}", responseCode, errorMessage);
                throw new RuntimeException("Gemini API error: " + responseCode + " - " + errorMessage);
            }
            
            String response = readStream(connection.getInputStream());
            log.debug("Gemini response: {} chars", response.length());
            
            return response;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Read input stream to string
     */
    private String readStream(java.io.InputStream stream) throws Exception {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    /**
     * Parse JSON response from Gemini API
     */
    private LlmDecisionResult parseGeminiResponse(String response, ProductData product) {
        try {
            log.debug("Parsing Gemini response: {}", response.substring(0, Math.min(200, response.length())));
            
            JsonObject responseObj = JsonParser.parseString(response).getAsJsonObject();
            
            // Extract text from candidates
            if (!responseObj.has("candidates") || responseObj.getAsJsonArray("candidates").size() == 0) {
                log.warn("No candidates in Gemini response");
                return fallbackDecision(product);
            }
            
            JsonObject candidate = responseObj.getAsJsonArray("candidates").get(0).getAsJsonObject();
            
            // Check for safety ratings that might block response
            if (candidate.has("safetyRatings")) {
                log.debug("Safety ratings present in response");
            }
            
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
            
            log.info("Successfully parsed Gemini response: verdict={}, confidence={}, pros={}, cons={}", 
                verdict, confidence, pros.length, cons.length);
            
            return new LlmDecisionResult(verdict, confidence, pros, cons, summary, reasoning);
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {} - {}", response, e.getMessage(), e);
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
            
            // Find JSON object boundaries
            int startIdx = cleanText.indexOf('{');
            int endIdx = cleanText.lastIndexOf('}');
            
            if (startIdx >= 0 && endIdx > startIdx) {
                cleanText = cleanText.substring(startIdx, endIdx + 1);
            }
            
            log.debug("Cleaned JSON: {}", cleanText);
            return JsonParser.parseString(cleanText).getAsJsonObject();
        } catch (Exception e) {
            log.warn("Failed to parse JSON from text: {} - {}", text, e.getMessage());
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
                int size = Math.min(array.size(), maxSize);
                String[] result = new String[size];
                for (int i = 0; i < size; i++) {
                    result[i] = array.get(i).getAsString();
                }
                log.debug("Parsed {} items for key: {}", result.length, key);
                return result;
            }
        } catch (Exception e) {
            log.debug("Failed to parse array for key: {} - {}", key, e.getMessage());
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
            new String[]{
                "Product shows moderate rating on market",
                "Consider reading recent customer reviews",
                "Compare with competitor alternatives in same price range",
                "Verify warranty and return policy with seller"
            },
            new String[]{
                "AI analysis service temporarily unavailable",
                "Validate all product specifications with official documentation",
                "Check current market pricing and discounts",
                "Read detailed customer reviews for this exact model",
                "Confirm availability and delivery timeframe"
            },
            "AI recommendation service is currently unavailable. Please analyze the product details manually or retry after some time.",
            "Unable to generate AI-powered recommendation. The decision is based on fallback analysis. Please verify product details and check current market conditions before making a purchase."
        );
    }
}