package com.sprintplanner.application;

import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.model.Team;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.in.PlanningInputPort;
import com.sprintplanner.domain.port.out.BreakdownPort;
import com.sprintplanner.domain.port.out.CapacityPort;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.DistributionPort;
import com.sprintplanner.domain.port.out.EstimationPort;
import com.sprintplanner.domain.port.out.MemoryReader;
import com.sprintplanner.domain.port.out.TeamRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PlanningOrchestrator implements PlanningInputPort {

    private final BreakdownPort breakdownPort;
    private final EstimationPort estimationPort;
    private final CapacityPort capacityPort;
    private final DistributionPort distributionPort;
    private final TeamRepository teamRepository;
    private final DeveloperRepository developerRepository;
    private final MemoryReader memoryReader;

    public PlanningOrchestrator(
        BreakdownPort breakdownPort,
        EstimationPort estimationPort,
        CapacityPort capacityPort,
        DistributionPort distributionPort,
        TeamRepository teamRepository,
        DeveloperRepository developerRepository,
        MemoryReader memoryReader
    ) {
        this.breakdownPort = breakdownPort;
        this.estimationPort = estimationPort;
        this.capacityPort = capacityPort;
        this.distributionPort = distributionPort;
        this.teamRepository = teamRepository;
        this.developerRepository = developerRepository;
        this.memoryReader = memoryReader;
    }
    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
    @Override
    public SprintPlan planSprint(PlanningRequest request) {
        validateRequest(request);

        Team team = teamRepository.findById(request.teamId())
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + request.teamId()));

        String teamProfileMemory = memoryReader.readTeamProfile(request.teamId());
        String sprintHistoryMemory = memoryReader.readSprintHistory(request.teamId());

        List<UserStory> brokenDown = breakdownPort.breakdown(request, teamProfileMemory, sprintHistoryMemory);
        sleep(1500);
        List<UserStory> estimated = estimationPort.estimate(brokenDown, sprintHistoryMemory);
        sleep(1500);
        var developers = developerRepository.findByTeamId(request.teamId());
        Map<java.util.UUID, Integer> capacityByDeveloper = capacityPort.calculateCapacity(
            developers,
            team.getSprintLengthDays(),
            request.daysOff() == null ? Map.of() : request.daysOff(),
            sprintHistoryMemory
        );
        Map<java.util.UUID, List<String>> developerCapabilities = developers.stream()
            .collect(java.util.stream.Collectors.toMap(
                com.sprintplanner.domain.model.Developer::getId,
                developer -> developer.getCapabilities().isEmpty()
                    ? inferCapabilitiesFromRole(developer.getRole())
                    : developer.getCapabilities(),
                (a, b) -> a,
                java.util.LinkedHashMap::new
            ));

        return distributionPort.distribute(
            request.teamId(),
            request.sprintGoal(),
            estimated,
            capacityByDeveloper,
            developerCapabilities,
            teamProfileMemory,
            sprintHistoryMemory
        );
    }

    private List<String> inferCapabilitiesFromRole(String roleLabel) {
        if (roleLabel == null || roleLabel.isBlank()) {
            return List.of();
        }
        String normalized = roleLabel.toLowerCase();
        List<String> capabilities = new java.util.ArrayList<>();
        if (normalized.contains("backend")) capabilities.add("backend");
        if (normalized.contains("frontend") || normalized.contains("front end")) capabilities.add("frontend");
        if (normalized.contains("fullstack") || normalized.contains("full stack")) {
            capabilities.add("backend");
            capabilities.add("frontend");
        }
        if (normalized.contains("qa") || normalized.contains("test")) capabilities.add("qa");
        if (normalized.contains("devops") || normalized.contains("sre")) capabilities.add("devops");
        if (normalized.contains("android")) capabilities.add("android");
        if (normalized.contains("ios")) capabilities.add("ios");
        if (normalized.contains("mobile")) {
            capabilities.add("android");
            capabilities.add("ios");
        }
        return capabilities;
    }

    private String normalizeCapability(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void validateRequest(PlanningRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Planning request must not be null.");
        }
        if (request.teamId() == null) {
            throw new IllegalArgumentException("teamId is required.");
        }
        if (request.sprintGoal() == null || request.sprintGoal().isBlank()) {
            throw new IllegalArgumentException("sprintGoal is required.");
        }
    }
}
