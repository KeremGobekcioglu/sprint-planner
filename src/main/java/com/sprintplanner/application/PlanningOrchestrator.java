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

    @Override
    public SprintPlan planSprint(PlanningRequest request) {
        validateRequest(request);

        Team team = teamRepository.findById(request.teamId())
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + request.teamId()));

        String teamProfileMemory = memoryReader.readTeamProfile(request.teamId());
        String sprintHistoryMemory = memoryReader.readSprintHistory(request.teamId());

        List<UserStory> brokenDown = breakdownPort.breakdown(request, teamProfileMemory, sprintHistoryMemory);
        List<UserStory> estimated = estimationPort.estimate(brokenDown, sprintHistoryMemory);

        Map<java.util.UUID, Integer> capacityByDeveloper = capacityPort.calculateCapacity(
            developerRepository.findByTeamId(request.teamId()),
            team.getSprintLengthDays(),
            request.daysOff() == null ? Map.of() : request.daysOff(),
            sprintHistoryMemory
        );

        return distributionPort.distribute(
            request.teamId(),
            request.sprintGoal(),
            estimated,
            capacityByDeveloper,
            teamProfileMemory,
            sprintHistoryMemory
        );
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
