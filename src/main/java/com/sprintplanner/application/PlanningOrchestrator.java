package com.sprintplanner.application;

import com.sprintplanner.domain.dto.CarryoverTaskDto;
import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.dto.SprintPlanMapper;
import com.sprintplanner.domain.dto.TaskDto;
import com.sprintplanner.domain.dto.UserStoryDto;
import com.sprintplanner.infrastructure.ai.GeminiDistributionAdapter;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.Team;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.in.PlanningInputPort;
import com.sprintplanner.domain.port.out.BreakdownPort;
import com.sprintplanner.domain.service.CapabilityInferenceService;
import com.sprintplanner.domain.port.out.CapacityPort;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.DistributionPort;
import com.sprintplanner.domain.port.out.EstimationPort;
import com.sprintplanner.domain.port.out.MemoryReader;
import com.sprintplanner.domain.port.out.ProgressPort;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.domain.port.out.TeamRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
            int rawCapacity = developers.stream().mapToInt(Developer::getBaseVelocity).sum();
            int totalDaysOff = request.daysOff() == null ? 0
                : request.daysOff().values().stream().mapToInt(Integer::intValue).sum();
            int devCount = developers.size();
            int sprintDays = team.getSprintLengthDays();
            double daysOffFactor = (devCount == 0 || sprintDays == 0) ? 1.0
                : Math.max(0.5, 1.0 - ((double) totalDaysOff / (devCount * sprintDays)));
            int totalCapacity = (int) (rawCapacity * 0.80 * daysOffFactor);

            Map<UUID, List<String>> developerCapabilities = developers.stream()
                .collect(java.util.stream.Collectors.toMap(
                    com.sprintplanner.domain.model.Developer::getId,
                    developer -> developer.getCapabilities().isEmpty()
                        ? CapabilityInferenceService.inferFromRole(developer.getRole())
                        : developer.getCapabilities(),
                    (a, b) -> a,
                    java.util.LinkedHashMap::new
                ));

            Map<UUID, String> developerNames = developers.stream()
                .collect(java.util.stream.Collectors.toMap(
                    Developer::getId,
                    Developer::getName,
                    (a, b) -> a,
                    java.util.LinkedHashMap::new
                ));

            push(userId, "capacity", "Calculating capacity for " + developers.size() + " developer(s)...");
            Map<UUID, Integer> capacityByDeveloper = capacityPort.calculateCapacity(
                developers,
                team.getSprintLengthDays(),
                request.daysOff() == null ? Map.of() : request.daysOff(),
                effectiveSprintHistory,
                request.teamId()
            );
            int adjustedCapacity = capacityByDeveloper.values().stream().mapToInt(Integer::intValue).sum();
            push(userId, "capacity", "Capacity ready: " + adjustedCapacity + " available story points across team.");
            log.info("[Planning] teamId={} — capacity: {} pts for {} devs", request.teamId(), adjustedCapacity, developers.size());

            // Build per-capability capacity budget for the breakdown prompt
            Map<String, Integer> capacityByCapability = new LinkedHashMap<>();
            for (Developer dev : developers) {
                int devCapacity = capacityByDeveloper.getOrDefault(dev.getId(), 0);
                for (String cap : developerCapabilities.getOrDefault(dev.getId(), List.of())) {
                    capacityByCapability.merge(cap.toLowerCase(), devCapacity, Integer::sum);
                }
            }

            // Find capabilities that have zero coverage (no developer has them)
            Set<String> allKnownCapabilities = Set.of("backend", "frontend", "qa", "devops", "android", "ios");
            Set<String> coveredCapabilities = capacityByCapability.keySet();
            Set<String> uncoveredCapabilities = allKnownCapabilities.stream()
                .filter(c -> !coveredCapabilities.contains(c))
                .collect(Collectors.toSet());

            StringBuilder devSummary = new StringBuilder();
            for (Developer dev : developers) {
                List<String> caps = dev.getCapabilities();
                if (caps != null && !caps.isEmpty()) {
                    devSummary.append("- ").append(dev.getName())
                        .append(": ").append(String.join(", ", caps))
                        .append(" (").append(capacityByDeveloper.getOrDefault(dev.getId(), 0)).append("pts)")
                        .append("\n");
                }
            }
            String developerSummary = devSummary.toString();

            int epicCount = request.epics() == null ? 0 : request.epics().size();
            push(userId, "breakdown", "Asking AI to break down " + epicCount + " epic(s) into user stories...");
            log.info("[Planning] teamId={} — breakdown start, {} epics", request.teamId(), epicCount);
            List<UserStory> brokenDown = new ArrayList<>(breakdownPort.breakdown(request, teamProfileMemory, effectiveSprintHistory, totalCapacity, capacityByCapability, uncoveredCapabilities, request.codebaseContext(), developerSummary));

            // Inject carry-over tasks as a synthetic story before estimation
            List<CarryoverTaskDto> carryovers = request.carryoverTasks();
            if (carryovers != null && !carryovers.isEmpty()) {
                UserStory carryoverStory = new UserStory(
                    UUID.randomUUID(), null, "Carry-over from previous sprint",
                    "Carry-over from previous sprint", 0, "PLANNED"
                );
                List<Task> carryoverTasks = new ArrayList<>();
                for (CarryoverTaskDto dto : carryovers) {
                    Task task = new Task(
                        UUID.randomUUID(), carryoverStory.getId(), dto.previousAssignedDevId(),
                        dto.taskTitle(), dto.description(), dto.acceptanceCriterion(),
                        dto.roleTag(), dto.storyPoints(), "PLANNED", null
                    );
                    carryoverTasks.add(task);
                }
                carryoverStory.setTasks(carryoverTasks);
                brokenDown.add(carryoverStory);
                push(userId, "breakdown", "Carry-over: injected " + carryovers.size() + " task(s) from previous sprint.");
                log.info("[Planning] teamId={} — injected {} carry-over tasks", request.teamId(), carryovers.size());
            }

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

            // Separate carry-over story from stories that need AI distribution
            List<UserStory> storiesForDistribution = new ArrayList<>();
            List<UserStory> carryoverStories = new ArrayList<>();
            for (UserStory story : estimated) {
                if (GeminiDistributionAdapter.CARRYOVER_EPIC_TITLE.equals(story.getEpicTitle())
                        || GeminiDistributionAdapter.CARRYOVER_EPIC_TITLE.equals(story.getTitle())) {
                    carryoverStories.add(story);
                } else {
                    storiesForDistribution.add(story);
                }
            }

            // Reserve capacity consumed by pre-assigned carry-over tasks so new tasks
            // are distributed against what is actually left for each developer
            Map<UUID, Integer> capacityForDistribution = new LinkedHashMap<>(capacityByDeveloper);
            for (UserStory story : carryoverStories) {
                if (story.getTasks() == null) continue;
                for (Task task : story.getTasks()) {
                    UUID devId = task.getAssignedDevId();
                    if (devId != null) {
                        capacityForDistribution.merge(devId, -task.getStoryPoints(), Integer::sum);
                    }
                }
            }

            int distTaskCount = storiesForDistribution.stream()
                .mapToInt(s -> s.getTasks() == null ? 0 : s.getTasks().size()).sum();
            push(userId, "distribution", "Assigning " + distTaskCount + " tasks to " + developers.size() + " developer(s)...");
            log.info("[Planning] teamId={} — distribution start", request.teamId());
            SprintPlan result = distributionPort.distribute(
                request.teamId(),
                request.sprintGoal(),
                storiesForDistribution,
                capacityForDistribution,
                developerCapabilities,
                developerNames,
                teamProfileMemory,
                effectiveSprintHistory
            );
            log.info("[Planning] teamId={} — distribution done", request.teamId());

            // Merge carry-over stories back with original assignments preserved
            if (!carryoverStories.isEmpty()) {
                List<UserStoryDto> allStories = new ArrayList<>(result.userStories());
                List<TaskDto> allUnassigned = new ArrayList<>(result.unassignedTasks());
                for (UserStory story : carryoverStories) {
                    List<TaskDto> assignedTaskDtos = new ArrayList<>();
                    if (story.getTasks() != null) {
                        for (Task task : story.getTasks()) {
                            if (task.getAssignedDevId() != null) {
                                task.setAssignmentReason("Carry-over — reassigned to original developer");
                                assignedTaskDtos.add(SprintPlanMapper.toDto(task));
                            } else {
                                task.setAssignmentReason("Carry-over — was unassigned in previous sprint, assign manually");
                                allUnassigned.add(SprintPlanMapper.toDto(task));
                            }
                        }
                    }
                    int storyPts = assignedTaskDtos.stream().mapToInt(TaskDto::storyPoints).sum();
                    allStories.add(new UserStoryDto(
                        story.getId(), story.getSprintId(), story.getEpicTitle(),
                        story.getTitle(), storyPts, story.getStatus(), assignedTaskDtos
                    ));
                }
                result = new SprintPlan(result.teamId(), result.sprintGoal(), allStories, allUnassigned,
                    result.sprintStatus(), result.confidence());
                log.info("[Planning] teamId={} — merged {} carry-over story/ies back into plan",
                    request.teamId(), carryoverStories.size());
            }

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
