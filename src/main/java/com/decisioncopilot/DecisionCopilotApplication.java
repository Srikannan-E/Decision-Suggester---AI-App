package com.decisioncopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class DecisionCopilotApplication {

	public static void main(String[] args) {
		SpringApplication.run(DecisionCopilotApplication.class, args);
	}

}

