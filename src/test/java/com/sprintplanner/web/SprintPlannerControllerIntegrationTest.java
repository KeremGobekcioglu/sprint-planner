package com.sprintplanner.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Team;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.TeamRepository;
import com.sprintplanner.web.dto.EpicHttpDto;
import com.sprintplanner.web.dto.PlanSprintHttpRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SprintPlannerControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private DeveloperRepository developerRepository;

    @Test
    void plansSprintOverHttpUsingStubbedPipeline() {
        UUID teamId = UUID.randomUUID();
        teamRepository.save(new Team(teamId, "HTTP Team", "GENERIC", 10, null, Instant.now()));
        developerRepository.save(new Developer(UUID.randomUUID(), teamId, "Kerem", "backend", 20));
        developerRepository.save(new Developer(UUID.randomUUID(), teamId, "Asli", "frontend", 16));

        PlanSprintHttpRequest request = new PlanSprintHttpRequest(
            teamId,
            List.of(new EpicHttpDto("Search upgrade", "Improve filtering and relevance")),
            "GENERIC",
            "Ship search improvements",
            Map.of()
        );

        ResponseEntity<SprintPlan> response = restTemplate.exchange(
            "http://localhost:" + port + "/api/planning/sprints/plan",
            HttpMethod.POST,
            new HttpEntity<>(request),
            SprintPlan.class
        );

        SprintPlan plan = response.getBody();
        assertNotNull(plan);
        assertNotNull(plan.userStories());
        assertFalse(plan.userStories().isEmpty());
    }
}
