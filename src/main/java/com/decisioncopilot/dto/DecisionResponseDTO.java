package com.decisioncopilot.dto;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for returning decision results to API clients
 * Converts JSON strings from database back to arrays
 */
public class DecisionResponseDTO {
    private UUID id;
    private UUID queryId;
    private String verdict;
    private BigDecimal confidenceScore;
    private String[] pros;
    private String[] cons;
    private String summary;
    private String reasoning;
    private String llmModel;
    private Long processingTimeMs;
    private String createdAt;

    private static final Gson gson = new Gson();

    public DecisionResponseDTO() {}

    /**
     * Convert DecisionResult entity to DTO (converts JSON strings back to arrays)
     */
    public static DecisionResponseDTO fromEntity(com.decisioncopilot.model.DecisionResult entity) {
        DecisionResponseDTO dto = new DecisionResponseDTO();
        dto.id = entity.getId();
        dto.queryId = entity.getQueryId();
        dto.verdict = entity.getVerdict();
        dto.confidenceScore = entity.getConfidenceScore();
        dto.summary = entity.getSummary();
        dto.reasoning = entity.getReasoning();
        dto.llmModel = entity.getLlmModel();
        dto.processingTimeMs = entity.getProcessingTimeMs();
        dto.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;

        // Parse JSON strings back to arrays
        try {
            dto.pros = gson.fromJson(entity.getPros(), String[].class);
            if (dto.pros == null) dto.pros = new String[0];
        } catch (Exception e) {
            dto.pros = new String[0];
        }

        try {
            dto.cons = gson.fromJson(entity.getCons(), String[].class);
            if (dto.cons == null) dto.cons = new String[0];
        } catch (Exception e) {
            dto.cons = new String[0];
        }

        return dto;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getQueryId() { return queryId; }
    public String getVerdict() { return verdict; }
    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public String[] getPros() { return pros; }
    public String[] getCons() { return cons; }
    public String getSummary() { return summary; }
    public String getReasoning() { return reasoning; }
    public String getLlmModel() { return llmModel; }
    public Long getProcessingTimeMs() { return processingTimeMs; }
    public String getCreatedAt() { return createdAt; }
}