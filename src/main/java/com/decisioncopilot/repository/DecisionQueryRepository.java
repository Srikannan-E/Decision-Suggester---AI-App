package com.decisioncopilot.repository;

import com.decisioncopilot.model.DecisionQuery;
import com.decisioncopilot.model.DecisionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface DecisionQueryRepository extends JpaRepository<DecisionQuery, UUID> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE DecisionQuery dq SET dq.status = :status WHERE dq.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") DecisionStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE DecisionQuery dq SET dq.status = :status, dq.errorMessage = :errorMessage WHERE dq.id = :id")
    void updateStatusWithError(@Param("id") UUID id, @Param("status") DecisionStatus status, @Param("errorMessage") String errorMessage);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE DecisionQuery dq SET dq.productId = :productId WHERE dq.id = :queryId")
    void linkProduct(@Param("queryId") UUID queryId, @Param("productId") UUID productId);
}