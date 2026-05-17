package com.decisioncopilot.repository;

import com.decisioncopilot.model.DecisionQuery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DecisionQueryRepository extends JpaRepository<DecisionQuery, UUID> {
}
