package com.decisioncopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@EnableCaching          // ← ADD THIS (for LLM response caching)
@EnableRetry            // ← ADD THIS (for Gemini API retries)
public class DecisionCopilotApplication {

	public static void main(String[] args) {
		SpringApplication.run(DecisionCopilotApplication.class, args);
	}

}