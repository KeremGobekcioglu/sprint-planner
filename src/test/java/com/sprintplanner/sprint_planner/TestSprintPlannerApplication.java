package com.sprintplanner.sprint_planner;

import com.sprintplanner.SprintPlannerApplication;
import org.springframework.boot.SpringApplication;

public class TestSprintPlannerApplication {

	public static void main(String[] args) {
		SpringApplication.from(SprintPlannerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
