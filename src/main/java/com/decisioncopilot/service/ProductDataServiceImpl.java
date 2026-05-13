package com.decisioncopilot.service;

import com.decisioncopilot.dto.ProductData;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Simulated product data fetcher
// Replace this class with a real scraper or API client in production
@Service
public class ProductDataServiceImpl implements ProductDataService {

    @Override
    public ProductData fetchProductData(String input, String category) {
        // Generate deterministic simulated data based on input
        // This keeps results consistent for the same product name
        int hash = Math.abs(input.hashCode());

        return new ProductData(
            input,
            generatePrice(hash),
            generateRating(hash),
            generateReviewCount(hash),
            category != null ? category : "General"
        );
    }

    private BigDecimal generatePrice(int hash) {
        // Simulated price between $19.99 and $999.99
        double price = 19.99 + (hash % 980);
        return BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal generateRating(int hash) {
        // Simulated rating between 2.5 and 5.0
        double rating = 2.5 + (hash % 25) / 10.0;
        return BigDecimal.valueOf(rating).setScale(2, RoundingMode.HALF_UP);
    }

    private int generateReviewCount(int hash) {
        // Simulated review count between 10 and 5000
        return 10 + (hash % 4990);
    }
}
