package com.decisioncopilot.service;

import com.decisioncopilot.dto.DecisionHistoryResponse;
import com.decisioncopilot.dto.DecisionResponseDTO;
import com.decisioncopilot.model.DecisionQuery;
import com.decisioncopilot.model.DecisionResult;
import com.decisioncopilot.model.DecisionStatus;
import com.decisioncopilot.repository.DecisionQueryRepository;
import com.decisioncopilot.repository.DecisionResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DecisionQueryService {

    private static final Logger log = LoggerFactory.getLogger(DecisionQueryService.class);

    private final DecisionQueryRepository queryRepository;
    private final DecisionResultRepository resultRepository;

    public DecisionQueryService(DecisionQueryRepository queryRepository, DecisionResultRepository resultRepository) {
        this.queryRepository = queryRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Get specific decision by ID
     * Returns DTO with converted arrays
     */
    public Object getDecisionById(UUID id) {
        DecisionQuery query = queryRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Decision query not found: " + id));

        if (query.getStatus() == DecisionStatus.PENDING || query.getStatus() == DecisionStatus.PROCESSING) {
            return new java.util.HashMap<String, Object>() {{
                put("id", id);
                put("status", query.getStatus().toString());
                put("message", "Decision is still being processed. Please check again in a moment.");
            }};
        }

        // Get the decision result
        DecisionResult result = resultRepository.findByQueryId(id)
            .orElseThrow(() -> new EntityNotFoundException("Decision result not found for query: " + id));

        // Convert to DTO (converts JSON strings back to arrays)
        return DecisionResponseDTO.fromEntity(result);
    }

    /**
     * Get decision history with pagination
     */
    public DecisionHistoryResponse getDecisionHistory(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<DecisionQuery> queries = queryRepository.findAll(pageable);

        var decisions = queries.getContent().stream()
            .map(query -> {
                var result = resultRepository.findByQueryId(query.getId());
                return result.map(DecisionResponseDTO::fromEntity).orElse(null);
            })
            .filter(dto -> dto != null)
            .collect(Collectors.toList());

        return new DecisionHistoryResponse(
            decisions,
            page,
            size,
            queries.getTotalElements(),
            queries.getTotalPages()
        );
    }
}