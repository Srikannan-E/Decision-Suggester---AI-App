package com.decisioncopilot.service;

import com.decisioncopilot.dto.ProductData;

public interface ProductDataService {
    ProductData fetchProductData(String input, String category);
}
