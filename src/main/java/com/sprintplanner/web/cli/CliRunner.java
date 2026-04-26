package com.sprintplanner.web.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.port.in.PlanningInputPort;
import com.sprintplanner.domain.port.out.PlanPublisher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class CliRunner implements CommandLineRunner {

    private final PlanningInputPort planningInputPort;
    private final PlanPublisher planPublisher;
    private final ObjectMapper objectMapper;

    public CliRunner(PlanningInputPort planningInputPort, PlanPublisher planPublisher, ObjectMapper objectMapper) {
        this.planningInputPort = planningInputPort;
        this.planPublisher = planPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length > 0 && args[0].startsWith("--file=")) {
            String filePath = args[0].substring("--file=".length());
            System.out.println("Starting CLI planning from file: " + filePath);
            
            try {
                PlanningRequest request = objectMapper.readValue(new File(filePath), PlanningRequest.class);
                SprintPlan plan = planningInputPort.planSprint(request);
                planPublisher.publish(request.teamId(), plan);
            } catch (Exception e) {
                System.err.println("Error processing CLI input: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
