package com.sprintplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.sprintplanner")
@EnableJpaRepositories(basePackages = "com.sprintplanner.infrastructure.persistence")
@EntityScan(basePackages = "com.sprintplanner.domain.model")
public class SprintPlannerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SprintPlannerApplication.class, args);
	}

}
