package com.sprintplanner.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sprintplanner.domain.dto.Epic;
import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Team;
import com.sprintplanner.domain.port.in.PlanningInputPort;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.TeamRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PlanningOrchestratorIntegrationTest {

    @Autowired
    private PlanningInputPort planningInputPort;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private DeveloperRepository developerRepository;

    @Test
    void generatesPlanThroughStubPipeline() {
        UUID teamId = UUID.randomUUID();
        Team team = new Team(teamId, "Alpha Team", "GENERIC", 10, null, Instant.now());
        teamRepository.save(team);

        Developer backendDev = new Developer(UUID.randomUUID(), teamId, "Kerem", "backend", 20);
        Developer frontendDev = new Developer(UUID.randomUUID(), teamId, "Asli", "frontend", 16);
        developerRepository.save(backendDev);
        developerRepository.save(frontendDev);

        PlanningRequest request = new PlanningRequest(
            teamId,
            List.of(new Epic("Checkout revamp", "Improve checkout reliability and UX")),
            "GENERIC",
            "Ship a reliable checkout path",
            Map.of()
        );

        SprintPlan sprintPlan = planningInputPort.planSprint(request);

        assertNotNull(sprintPlan);
        assertFalse(sprintPlan.userStories().isEmpty());
        assertFalse(sprintPlan.userStories().getFirst().getTasks().isEmpty());
        assertNotNull(sprintPlan.userStories().getFirst().getTasks().getFirst().getAssignedDevId());
    }
}
