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

    @Column(name = "query_id")
    private UUID queryId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "verdict", columnDefinition = "VARCHAR(50)")
    private String verdict;

    @Column(name = "confidence_score")
    private BigDecimal confidenceScore;

    // FIXED: Store as TEXT (JSON string, not array)
    @Column(name = "pros", columnDefinition = "TEXT")
    private String pros;

    // FIXED: Store as TEXT (JSON string, not array)
    @Column(name = "cons", columnDefinition = "TEXT")
    private String cons;

    // FIXED: Store as TEXT
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    // FIXED: Store as TEXT
    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "llm_model")
    private String llmModel;

    @Column(name = "token_usage")
    private Integer tokenUsage;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Constructors
    public DecisionResult() {}

    public DecisionResult(UUID queryId, UUID productId, String verdict, BigDecimal confidenceScore, 
                         String pros, String cons, String summary, String reasoning, 
                         String llmModel, Integer tokenUsage, Long processingTimeMs, LocalDateTime createdAt) {
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

    // Getters and Setters
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

    public String getPros() { return pros; }
    public void setPros(String pros) { this.pros = pros; }

    public String getCons() { return cons; }
    public void setCons(String cons) { this.cons = cons; }

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