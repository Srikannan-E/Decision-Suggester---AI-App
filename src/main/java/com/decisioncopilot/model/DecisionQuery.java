package com.decisioncopilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "decision_queries")
public class DecisionQuery {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    private UUID productId;
    private String inputText;
    private String category;
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(20)")
    private DecisionStatus status;
    private String errorMessage;
    private LocalDateTime createdAt;

    public DecisionQuery() {}

    public DecisionQuery(UUID id, UUID productId, String inputText, String category, DecisionStatus status, String errorMessage, LocalDateTime createdAt) {
        this.id = id;
        this.productId = productId;
        this.inputText = inputText;
        this.category = category;
        this.status = status;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = inputText; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public DecisionStatus getStatus() { return status; }
    public void setStatus(DecisionStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}