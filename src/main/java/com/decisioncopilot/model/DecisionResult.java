package com.decisioncopilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "decision_results")
public class DecisionResult {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    private UUID queryId;
    private UUID productId;
    @Column(columnDefinition = "VARCHAR(20)")
    private String verdict;
    private BigDecimal confidenceScore;
    private String[] pros;
    private String[] cons;
    private String summary;
    private String reasoning;
    private String llmModel;
    private Integer tokenUsage;
    private Long processingTimeMs;
    private LocalDateTime createdAt;

    public DecisionResult() {}

    public DecisionResult(UUID queryId, UUID productId, String verdict, BigDecimal confidenceScore, String[] pros, String[] cons, String summary, String reasoning, String llmModel, Integer tokenUsage, Long processingTimeMs, LocalDateTime createdAt) {
        this.queryId = queryId;
        this.productId = productId;
        this.verdict = verdict;
        this.confidenceScore = confidenceScore;
        this.pros = pros;
        this.cons = cons;
        this.summary = summary;
        this.reasoning = reasoning;
        this.llmModel = llmModel;
        this.tokenUsage = tokenUsage;
        this.processingTimeMs = processingTimeMs;
        this.createdAt = createdAt;
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getQueryId() { return queryId; }
    public void setQueryId(UUID queryId) { this.queryId = queryId; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }
    public String[] getPros() { return pros; }
    public void setPros(String[] pros) { this.pros = pros; }
    public String[] getCons() { return cons; }
    public void setCons(String[] cons) { this.cons = cons; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
    public Integer getTokenUsage() { return tokenUsage; }
    public void setTokenUsage(Integer tokenUsage) { this.tokenUsage = tokenUsage; }
    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}