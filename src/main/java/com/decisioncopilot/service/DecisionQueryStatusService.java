package com.decisioncopilot.service;

import com.decisioncopilot.model.DecisionQuery;
import com.decisioncopilot.model.DecisionStatus;
import com.decisioncopilot.repository.DecisionQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DecisionQueryStatusService {

    private final DecisionQueryRepository queryRepository;

    public DecisionQueryStatusService(DecisionQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(UUID queryId, DecisionStatus status) {
        DecisionQuery query = queryRepository.findById(queryId)
            .orElseThrow(() -> new IllegalStateException("Decision query not found: " + queryId));
        query.setStatus(status);
        queryRepository.saveAndFlush(query);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatusWithError(UUID queryId, DecisionStatus status, String errorMessage) {
        DecisionQuery query = queryRepository.findById(queryId)
            .orElseThrow(() -> new IllegalStateException("Decision query not found: " + queryId));
        query.setStatus(status);
        query.setErrorMessage(truncate(errorMessage));
        queryRepository.saveAndFlush(query);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void linkProduct(UUID queryId, UUID productId) {
        DecisionQuery query = queryRepository.findById(queryId)
            .orElseThrow(() -> new IllegalStateException("Decision query not found: " + queryId));
        query.setProductId(productId);
        queryRepository.saveAndFlush(query);
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
