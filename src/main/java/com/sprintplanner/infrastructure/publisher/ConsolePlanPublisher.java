package com.sprintplanner.infrastructure.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.port.out.PlanPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ConsolePlanPublisher implements PlanPublisher {

    private final ObjectMapper objectMapper;

    public ConsolePlanPublisher() {
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void publish(UUID teamId, SprintPlan sprintPlan) {
        try {
            System.out.println("====== SPRINT PLAN FOR TEAM " + teamId + " ======");
            System.out.println(objectMapper.writeValueAsString(sprintPlan));
            System.out.println("=================================================");
        } catch (Exception e) {
            System.err.println("Failed to print sprint plan to console: " + e.getMessage());
        }
    }
}
