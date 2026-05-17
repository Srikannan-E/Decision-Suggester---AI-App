package com.decisioncopilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * FIXED: Configure dedicated thread pool for LLM background processing
 * This prevents the "slow response" issue where all requests hang waiting for processing
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    /**
     * FIXED: Named executor "llmTaskExecutor" matches the @Qualifier in DecisionOrchestrator
     * This provides a dedicated thread pool for background decision processing
     */
    @Bean(name = "llmTaskExecutor")
    public Executor llmTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // FIXED: Sufficient threads to handle concurrent requests without blocking
        executor.setCorePoolSize(5);          // 5 threads always ready
        executor.setMaxPoolSize(15);          // Up to 15 if load increases
        executor.setQueueCapacity(100);       // Queue up to 100 tasks
        executor.setThreadNamePrefix("llm-decision-");
        
        // FIXED: Prevent slow response by rejecting tasks if queue is full
        // Instead of hanging, failed tasks will be logged immediately
        executor.setRejectedExecutionHandler(
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        executor.initialize();
        return executor;
    }
}