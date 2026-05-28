package com.sprintplanner.application;

import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Team;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.in.PlanningInputPort;
import com.sprintplanner.domain.port.out.BreakdownPort;
import com.sprintplanner.domain.port.out.CapacityPort;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.DistributionPort;
import com.sprintplanner.domain.port.out.EstimationPort;
import com.sprintplanner.domain.port.out.MemoryReader;
import com.sprintplanner.domain.port.out.ProgressPort;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.domain.port.out.TeamRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PlanningOrchestrator implements PlanningInputPort {

    private static final Logger log = LoggerFactory.getLogger(PlanningOrchestrator.class);

    private final BreakdownPort breakdownPort;
    private final EstimationPort estimationPort;
    private final CapacityPort capacityPort;
    private final DistributionPort distributionPort;
    private final TeamRepository teamRepository;
    private final DeveloperRepository developerRepository;
    private final MemoryReader memoryReader;
    private final SprintRepository sprintRepository;
    private final ProgressPort progressPort;

    public PlanningOrchestrator(
        BreakdownPort breakdownPort,
        EstimationPort estimationPort,
        CapacityPort capacityPort,
        DistributionPort distributionPort,
        TeamRepository teamRepository,
        DeveloperRepository developerRepository,
        MemoryReader memoryReader,
        SprintRepository sprintRepository,
        ProgressPort progressPort
    ) {
        this.breakdownPort = breakdownPort;
        this.estimationPort = estimationPort;
        this.capacityPort = capacityPort;
        this.distributionPort = distributionPort;
        this.teamRepository = teamRepository;
        this.developerRepository = developerRepository;
        this.memoryReader = memoryReader;
        this.sprintRepository = sprintRepository;
        this.progressPort = progressPort;
    }

    @Override
    public SprintPlan planSprint(PlanningRequest request) {
        validateRequest(request);
        UUID userId = request.userId();

        try {
            if (userId != null) progressPort.start(userId);

            push(userId, "input", "Validating request and loading team data...");
            Team team = teamRepository.findById(request.teamId())
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + request.teamId()));

            boolean hasPriorHistory = sprintRepository.countCompletedByTeamId(request.teamId()) > 0;
            if (hasPriorHistory) {
                push(userId, "memory", "Loading team preferences and sprint history...");
            } else {
                push(userId, "memory", "No prior sprint history — starting fresh.");
            }

            String teamProfileMemory = hasPriorHistory ? memoryReader.readTeamProfile(request.teamId()) : "";
            String sprintHistoryMemory = hasPriorHistory ? memoryReader.readSprintHistory(request.teamId()) : "";

            // Use imported context as a one-time substitute when no persistent history exists
            String effectiveSprintHistory = sprintHistoryMemory;
            if ((sprintHistoryMemory == null || sprintHistoryMemory.isBlank())
                    && request.previousSprintContext() != null
                    && !request.previousSprintContext().isBlank()) {
                effectiveSprintHistory = request.previousSprintContext();
                push(userId, "memory", "Using imported sprint context for this planning run (not persisted).");
                log.info("[Planning] teamId={} — using previousSprintContext as one-time history substitute", request.teamId());
            }

            var developers = developerRepository.findByTeamId(request.teamId());
            int totalCapacity = developers.stream().mapToInt(Developer::getBaseVelocity).sum();

            int epicCount = request.epics() == null ? 0 : request.epics().size();
            push(userId, "breakdown", "Asking AI to break down " + epicCount + " epic(s) into user stories...");
            log.info("[Planning] teamId={} — breakdown start, {} epics", request.teamId(), epicCount);
            List<UserStory> brokenDown = breakdownPort.breakdown(request, teamProfileMemory, effectiveSprintHistory, totalCapacity);
            int storyCount = brokenDown.size();
            int taskCount = brokenDown.stream().mapToInt(s -> s.getTasks() == null ? 0 : s.getTasks().size()).sum();
            push(userId, "breakdown", "Breakdown complete: " + storyCount + " stories, " + taskCount + " tasks.");
            log.info("[Planning] teamId={} — breakdown done: {} stories, {} tasks", request.teamId(), storyCount, taskCount);

            push(userId, "estimation", "Estimating story points for " + taskCount + " tasks...");
            log.info("[Planning] teamId={} — estimation start", request.teamId());
            List<UserStory> estimated = estimationPort.estimate(brokenDown, effectiveSprintHistory);
            int totalPoints = estimated.stream().mapToInt(s -> s.getTasks() == null ? 0 :
                s.getTasks().stream().mapToInt(t -> t.getStoryPoints()).sum()).sum();
            push(userId, "estimation", "Estimation done: " + totalPoints + " total story points.");
            log.info("[Planning] teamId={} — estimation done: {} total points", request.teamId(), totalPoints);

            push(userId, "capacity", "Calculating capacity for " + developers.size() + " developer(s)...");
            Map<UUID, Integer> capacityByDeveloper = capacityPort.calculateCapacity(
                developers,
                team.getSprintLengthDays(),
                request.daysOff() == null ? Map.of() : request.daysOff(),
                effectiveSprintHistory
            );
            int adjustedCapacity = capacityByDeveloper.values().stream().mapToInt(Integer::intValue).sum();
            push(userId, "capacity", "Capacity ready: " + adjustedCapacity + " available story points across team.");
            log.info("[Planning] teamId={} — capacity: {} pts for {} devs", request.teamId(), adjustedCapacity, developers.size());

            Map<UUID, List<String>> developerCapabilities = developers.stream()
                .collect(java.util.stream.Collectors.toMap(
                    com.sprintplanner.domain.model.Developer::getId,
                    developer -> developer.getCapabilities().isEmpty()
                        ? inferCapabilitiesFromRole(developer.getRole())
                        : developer.getCapabilities(),
                    (a, b) -> a,
                    java.util.LinkedHashMap::new
                ));

            push(userId, "distribution", "Assigning " + taskCount + " tasks to " + developers.size() + " developer(s)...");
            log.info("[Planning] teamId={} — distribution start", request.teamId());
            SprintPlan result = distributionPort.distribute(
                request.teamId(),
                request.sprintGoal(),
                estimated,
                capacityByDeveloper,
                developerCapabilities,
                teamProfileMemory,
                effectiveSprintHistory
            );
            log.info("[Planning] teamId={} — distribution done", request.teamId());

            if (userId != null) progressPort.complete(userId);
            return result;

        } catch (Exception e) {
            log.error("[Planning] teamId={} — pipeline failed: {}", request.teamId(), e.getMessage(), e);
            if (userId != null) progressPort.fail(userId, "Pipeline error: " + e.getMessage());
            throw e;
        }
    }

    private void push(UUID userId, String phase, String message) {
        if (userId != null) progressPort.push(userId, phase, message);
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
