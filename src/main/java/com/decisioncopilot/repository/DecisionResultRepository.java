package com.decisioncopilot.repository;

import com.decisioncopilot.model.DecisionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DecisionResultRepository extends JpaRepository<DecisionResult, UUID> {
    Optional<DecisionResult> findByQueryId(UUID queryId);
}