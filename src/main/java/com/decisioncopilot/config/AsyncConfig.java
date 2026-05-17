package com.decisioncopilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * FIXED: Single configuration for async thread pool
 * This replaces the duplicate ThreadPoolConfig.java
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * FIXED: Dedicated thread pool for LLM background decision processing
     * Prevents "slow response" issue where requests hang during async processing
     * 
     * Configuration:
     * - Core threads: 5 (always ready for new tasks)
     * - Max threads: 15 (scales up under load)
     * - Queue capacity: 100 (buffer for pending tasks)
     * - Rejection policy: CallerRunsPolicy (execute in calling thread if queue full)
     */
    @Bean(name = "llmTaskExecutor")
    public Executor llmTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core and max pool sizes for handling concurrent requests
        executor.setCorePoolSize(5);                    // Always 5 threads ready
        executor.setMaxPoolSize(15);                    // Can scale to 15 if needed
        executor.setQueueCapacity(100);                 // Buffer up to 100 pending tasks
        executor.setThreadNamePrefix("llm-decision-");  // Identify threads in logs
        
        // Graceful shutdown configuration
        executor.setWaitForTasksToCompleteOnShutdown(true);  // Wait for tasks before shutdown
        executor.setAwaitTerminationSeconds(30);             // Max 30 seconds to wait
        
        // Rejection policy: if queue is full, execute in caller's thread
        // This prevents task rejection exceptions and provides feedback
        executor.setRejectedExecutionHandler(
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        executor.initialize();
        return executor;
    }
}