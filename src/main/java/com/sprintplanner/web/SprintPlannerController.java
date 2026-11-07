
package com.sprintplanner.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintplanner.application.ConfidenceCalculator;
import com.sprintplanner.application.PreferenceService;
import com.sprintplanner.application.RetrospectiveService;
import com.sprintplanner.application.SprintReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sprintplanner.domain.dto.RetrospectiveReport;
import com.sprintplanner.domain.dto.Epic;
import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.dto.SprintConfidenceForecast;
import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.dto.SprintPlanMapper;
import com.sprintplanner.domain.dto.TaskDto;
import com.sprintplanner.domain.dto.UserStoryDto;
import com.sprintplanner.domain.model.AppUser;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.EditLog;
import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.Team;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.in.PlanningInputPort;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.EditLogRepository;
import com.sprintplanner.domain.port.out.MemoryReader;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.domain.port.out.TeamRepository;
import com.sprintplanner.infrastructure.ai.GeminiClientProvider;
import com.sprintplanner.infrastructure.persistence.JpaUserRepository;
import com.sprintplanner.infrastructure.persistence.TaskJpaRepository;
import com.sprintplanner.infrastructure.progress.PlanningProgressStore;
import com.sprintplanner.infrastructure.publisher.TrelloPlanPublisher;
import com.sprintplanner.web.dto.LogEditHttpRequest;
import com.sprintplanner.web.dto.PlanComparisonResponse;
import com.sprintplanner.web.dto.PlanSprintHttpRequest;
import com.sprintplanner.web.dto.SprintReviewSummary;
import com.sprintplanner.web.dto.TaskGuidanceResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.sprintplanner.infrastructure.ratelimit.AiUsageLimiter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/sprints")
public class SprintPlannerController {

    private static final Logger logger = LoggerFactory.getLogger(SprintPlannerController.class);
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
    );

    private final PlanningInputPort planningInputPort;
    private final SprintRepository sprintRepository;
    private final MemoryReader memoryReader;
    private final EditLogRepository editLogRepository;
    private final TeamRepository teamRepository;
    private final DeveloperRepository developerRepository;
    private final PreferenceService preferenceService;
    private final PlanningProgressStore progressStore;
    private final TrelloPlanPublisher trelloPlanPublisher;
    private final JpaUserRepository userRepository;
    private final TaskJpaRepository taskJpaRepository;
    private final GeminiClientProvider geminiClientProvider;
    private final ObjectMapper objectMapper;
    private final ConfidenceCalculator confidenceCalculator;
    private final RetrospectiveService retrospectiveService;
    private final SprintReviewService sprintReviewService;
    private final AccessGuard accessGuard;
    private final AiUsageLimiter aiUsageLimiter;

    public SprintPlannerController(PlanningInputPort planningInputPort, SprintRepository sprintRepository,
            MemoryReader memoryReader, EditLogRepository editLogRepository, TeamRepository teamRepository,
            DeveloperRepository developerRepository, PreferenceService preferenceService,
            PlanningProgressStore progressStore, TrelloPlanPublisher trelloPlanPublisher,
            JpaUserRepository userRepository, TaskJpaRepository taskJpaRepository,
            GeminiClientProvider geminiClientProvider, ObjectMapper objectMapper,
            ConfidenceCalculator confidenceCalculator, RetrospectiveService retrospectiveService,
            SprintReviewService sprintReviewService,
            AccessGuard accessGuard, AiUsageLimiter aiUsageLimiter) {
        this.planningInputPort = planningInputPort;
        this.sprintRepository = sprintRepository;
        this.memoryReader = memoryReader;
        this.editLogRepository = editLogRepository;
        this.teamRepository = teamRepository;
        this.developerRepository = developerRepository;
        this.preferenceService = preferenceService;
        this.progressStore = progressStore;
        this.trelloPlanPublisher = trelloPlanPublisher;
        this.userRepository = userRepository;
        this.taskJpaRepository = taskJpaRepository;
        this.geminiClientProvider = geminiClientProvider;
        this.objectMapper = objectMapper;
        this.confidenceCalculator = confidenceCalculator;
        this.retrospectiveService = retrospectiveService;
        this.sprintReviewService = sprintReviewService;
        this.accessGuard = accessGuard;
        this.aiUsageLimiter = aiUsageLimiter;
    }

    @PostMapping("/plan")
    public ResponseEntity<SprintPlan> planSprint(@RequestBody PlanSprintHttpRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        accessGuard.requireTeam(request.teamId());
        validatePlanInput(request);
        aiUsageLimiter.consume(currentUserId, SecurityUtils.isAdmin(), AiUsageLimiter.WEIGHT_PLAN);
        PlanningRequest planningRequest = new PlanningRequest(
            request.teamId(),
            currentUserId,
            request.epics() == null
                ? List.of()
                : request.epics().stream().map(epic -> new Epic(epic.title(), epic.description())).toList(),
            request.domain(),
            request.sprintGoal(),
            request.daysOff() == null ? Map.of() : request.daysOff(),
            request.previousSprintContext(),
            request.carryoverTasks(),
            request.codebaseContext()
        );
        
        SprintPlan plan = planningInputPort.planSprint(planningRequest);
        
        // Create and save Sprint entity to database
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint(
            sprintId,
            request.teamId(),
            (int) sprintRepository.countByTeamId(request.teamId()) + 1,
            request.sprintGoal(),
            request.domain(),
            Sprint.Status.PLANNING,
            Instant.now(),
            null
        );
        
        // Convert DTOs back to entities and set sprint references before persisting
        List<UserStory> userStories = new ArrayList<>();
        if (plan.userStories() != null) {
            for (UserStoryDto storyDto : plan.userStories()) {
                UserStory story = SprintPlanMapper.fromDto(storyDto);
                story.setSprintId(sprintId);
                story.setSprint(sprint);
                // wire task back-references
                if (story.getTasks() != null) {
                    for (Task task : story.getTasks()) {
                        task.setStory(story);
                    }
                }
                userStories.add(story);
            }
        }

        // Without this, tasks the distribution adapter marks unassigned are dropped from the DB entirely.
        if (plan.unassignedTasks() != null && !plan.unassignedTasks().isEmpty()) {
            Map<UUID, UserStory> storyById = new HashMap<>();
            for (UserStory story : userStories) {
                storyById.put(story.getId(), story);
            }
            for (TaskDto unassignedDto : plan.unassignedTasks()) {
                if (unassignedDto.storyId() == null) continue;
                UserStory parentStory = storyById.get(unassignedDto.storyId());
                if (parentStory == null) continue;
                Task task = SprintPlanMapper.fromDto(unassignedDto);
                task.setStory(parentStory);
                parentStory.getTasks().add(task);
            }
        }

        sprint.setCodebaseContext(request.codebaseContext());
        sprint.setUserStories(userStories);
        sprintRepository.save(sprint);

        // Compute confidence forecast from total committed points
        int totalPoints = 0;
        if (plan.userStories() != null) {
            for (UserStoryDto s : plan.userStories()) {
                totalPoints += s.storyPoints();
            }
        }
        if (plan.unassignedTasks() != null) {
            for (TaskDto t : plan.unassignedTasks()) {
                totalPoints += t.storyPoints();
            }
        }
        SprintConfidenceForecast forecast = confidenceCalculator.calculate(request.teamId(), totalPoints);

        SprintPlan planWithId = new SprintPlan(
                plan.teamId(),
                plan.sprintGoal(),
                plan.userStories() == null ? List.of() : plan.userStories().stream()
                        .map(story -> new UserStoryDto(
                                story.id(),
                                sprintId,
                                story.epicTitle(),
                                story.title(),
                                story.storyPoints(),
                                story.status(),
                                story.tasks()
                        ))
                        .toList(),
                plan.unassignedTasks() == null ? List.of() : plan.unassignedTasks(),
                "PLANNING",
                forecast
        );

        return ResponseEntity.ok(planWithId);
    }

    @GetMapping("/active")
    public ResponseEntity<Map<String, String>> getActiveSprint() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        List<UUID> teamIds = isAdmin
            ? teamRepository.findAll().stream().map(team -> team.getId()).toList()
            : teamRepository.findByOwnerId(currentUserId).stream().map(team -> team.getId()).toList();

        List<Sprint> activeSprints = sprintRepository.findActiveForTeams(teamIds);
        if (activeSprints.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        Sprint active = activeSprints.get(0);
        return ResponseEntity.ok(Map.of("sprintId", active.getId().toString(), "status", active.getStatus().name()));
    }

    @GetMapping("/plan/status")
    public ResponseEntity<List<PlanningProgressStore.Step>> getPlanningStatus() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<PlanningProgressStore.Step> steps = progressStore.get(userId);
        return ResponseEntity.ok(steps);
    }

    @GetMapping("/teams/{teamId}/confidence")
    public ResponseEntity<SprintConfidenceForecast> getConfidenceForecast(
            @PathVariable UUID teamId,
            @RequestParam int points) {
        accessGuard.requireTeam(teamId);
        SprintConfidenceForecast forecast = confidenceCalculator.calculate(teamId, points);
        return ResponseEntity.ok(forecast);
    }

    @GetMapping("/teams/{teamId}/planning-mode")
    public ResponseEntity<Map<String, Object>> getPlanningMode(@PathVariable UUID teamId) {
        accessGuard.requireTeam(teamId);
long completedSprints = sprintRepository.countCompletedByTeamId(teamId);
        String teamProfile = memoryReader.readTeamProfile(teamId);
        String sprintHistory = memoryReader.readSprintHistory(teamId);

        String lastCodebaseContext = sprintRepository.findByTeamId(teamId).stream()
            .filter(s -> s.getCodebaseContext() != null && !s.getCodebaseContext().isBlank())
            .max(Comparator.comparing(Sprint::getPlannedAt))
            .map(Sprint::getCodebaseContext)
            .orElse(null);

        Map<String, Object> body = new HashMap<>();
        body.put("mode", completedSprints > 0 ? "CONTINUOUS" : "FRESH");
        body.put("completedSprints", completedSprints);
        body.put("hasTeamProfile", teamProfile != null && !teamProfile.isBlank());
        body.put("hasSprintHistory", sprintHistory != null && !sprintHistory.isBlank());
        if (lastCodebaseContext != null) {
            body.put("lastCodebaseContext", lastCodebaseContext);
        }

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{sprintId}/plan")
    public ResponseEntity<SprintPlan> getSprintPlan(@PathVariable String sprintId) {
        UUID normalizedSprintId = normalizeSprintId(sprintId);
        accessGuard.requireSprint(normalizedSprintId);
        Sprint sprint = sprintRepository.findWithStoriesAndTasksById(normalizedSprintId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found: " + normalizedSprintId));

        SprintPlan plan = SprintPlanMapper.toSprintPlan(sprint);
        return ResponseEntity.ok(plan);
    }

    @PostMapping("/{sprintId}/approve")
    public ResponseEntity<String> approveSprint(@PathVariable String sprintId) {
        UUID normalizedSprintId = normalizeSprintId(sprintId);
        Sprint sprint = accessGuard.requireSprint(normalizedSprintId);

        sprint.setStatus(Sprint.Status.ACTIVE);
        sprint.setReviewedAt(Instant.now());
        sprintRepository.save(sprint);

        // Rebuild team_profile.md from accumulated edit logs
        var editLogs = editLogRepository.findBySprintId(normalizedSprintId);
        preferenceService.updateTeamProfile(sprint.getTeamId(), editLogs);

        return ResponseEntity.ok("Sprint approved and moved to ACTIVE.");
    }

    @PostMapping("/{sprintId}/edits")
    @Transactional
    public ResponseEntity<String> logEdit(@PathVariable String sprintId, @RequestBody LogEditHttpRequest request) {
        UUID normalizedSprintId = normalizeSprintId(sprintId);
        accessGuard.requireSprint(normalizedSprintId);

        UUID taskId = null;
        if (request.entityId() != null) {
            try { taskId = UUID.fromString(request.entityId()); } catch (IllegalArgumentException ignored) {}
        }
        if (taskId != null) {
            // the task must belong to this (already authorised) sprint
            accessGuard.requireTaskInSprint(taskId, normalizedSprintId);
        }

        EditLog log = new EditLog(
            UUID.randomUUID(),
            normalizedSprintId,
            request.field(),
            request.oldValue(),
            request.newValue(),
            request.taskType(),
            Instant.now()
        );
        log.setTaskId(taskId);
        editLogRepository.save(log);

        // Persist field changes on the task entity so submitReview reads current PM assignments
        if (taskId != null) {
            final UUID finalTaskId = taskId;
            if ("assignedDevId".equals(request.field())) {
                taskJpaRepository.findById(finalTaskId).ifPresent(task -> {
                    UUID newDevId = null;
                    String newVal = request.newValue();
                    if (newVal != null && !"unassigned".equals(newVal)) {
                        try { newDevId = UUID.fromString(newVal); } catch (IllegalArgumentException ignored) {}
                    }
                    task.setAssignedDevId(newDevId);
                    taskJpaRepository.save(task);
                });
            } else if ("storyPoints".equals(request.field()) && request.newValue() != null) {
                taskJpaRepository.findById(finalTaskId).ifPresent(task -> {
                    try {
                        task.setStoryPoints(Integer.parseInt(request.newValue()));
                        taskJpaRepository.save(task);
                    } catch (NumberFormatException ignored) {}
                });
            } else if ("deferred".equals(request.field()) && request.newValue() != null) {
                taskJpaRepository.findById(finalTaskId).ifPresent(task -> {
                    task.setDeferred(Boolean.parseBoolean(request.newValue()));
                    taskJpaRepository.save(task);
                });
            }
        }

        return ResponseEntity.ok("Edit logged.");
    }

    @GetMapping("/{sprintId}/plan/comparison")
    public ResponseEntity<PlanComparisonResponse> getSprintPlanComparison(@PathVariable String sprintId) {
        UUID normalizedSprintId = normalizeSprintId(sprintId);
        accessGuard.requireSprint(normalizedSprintId);
        Sprint sprint = sprintRepository.findWithStoriesAndTasksById(normalizedSprintId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found: " + normalizedSprintId));

        List<EditLog> editLogs = editLogRepository.findBySprintId(normalizedSprintId);

        // Per task, per field: keep only the EARLIEST original value (that's the AI's original plan)
        Map<UUID, Map<String, String>> taskOriginals = new HashMap<>();
        editLogs.stream()
            .filter(e -> e.getTaskId() != null)
            .filter(e -> "assignedDevId".equals(e.getField()) || "storyPoints".equals(e.getField()))
            .sorted(Comparator.comparing(EditLog::getLoggedAt))
            .forEach(e -> taskOriginals
                .computeIfAbsent(e.getTaskId(), k -> new HashMap<>())
                .putIfAbsent(e.getField(), e.getOriginalValue()));

        List<TaskDto> aiPlan = new ArrayList<>();
        List<TaskDto> finalPlan = new ArrayList<>();
        Set<UUID> changedTaskIds = new HashSet<>();
        int aiTotalPoints = 0;
        int finalTotalPoints = 0;

        if (sprint.getUserStories() != null) {
            for (UserStory story : sprint.getUserStories()) {
                if (story.getTasks() == null) continue;
                for (Task task : story.getTasks()) {
                    TaskDto finalTask = SprintPlanMapper.toDto(task);
                    finalPlan.add(finalTask);
                    finalTotalPoints += task.getStoryPoints();

                    Map<String, String> originals = taskOriginals.get(task.getId());
                    if (originals != null && !originals.isEmpty()) {
                        changedTaskIds.add(task.getId());
                        UUID aiDevId = task.getAssignedDevId();
                        int aiPoints = task.getStoryPoints();
                        if (originals.containsKey("assignedDevId")) {
                            String orig = originals.get("assignedDevId");
                            if (orig == null || "unassigned".equals(orig)) {
                                aiDevId = null;
                            } else {
                                try { aiDevId = UUID.fromString(orig); } catch (IllegalArgumentException ignored) {}
                            }
                        }
                        if (originals.containsKey("storyPoints")) {
                            try { aiPoints = Integer.parseInt(originals.get("storyPoints")); } catch (NumberFormatException ignored) {}
                        }
                        aiTotalPoints += aiPoints;
                        aiPlan.add(new TaskDto(
                            task.getId(), task.getStoryId(), aiDevId, task.getTitle(),
                            task.getDescription(), task.getAcceptanceCriterion(),
                            task.getRoleTag(), aiPoints, task.getStatus(),
                            task.getTrelloCardId(), task.getAssignmentReason(), task.isDeferred()
                        ));
                    } else {
                        aiTotalPoints += task.getStoryPoints();
                        aiPlan.add(finalTask);
                    }
                }
            }
        }

        return ResponseEntity.ok(new PlanComparisonResponse(
            aiPlan, finalPlan, changedTaskIds.size(), finalTotalPoints - aiTotalPoints, changedTaskIds
        ));
    }

    @PostMapping("/{sprintId}/trello-push")
    public ResponseEntity<String> pushToTrello(@PathVariable String sprintId) {
        UUID normalizedSprintId = normalizeSprintId(sprintId);
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        AppUser user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String userToken = user.getTrelloToken();
        if (userToken == null || userToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Connect your Trello account first. Go to Profile → Connect Trello.");
        }

        Sprint sprint = accessGuard.requireSprint(normalizedSprintId);

        Team team = accessGuard.requireTeam(sprint.getTeamId());

        if (team.getTrelloBoardId() == null || team.getTrelloBoardId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No Trello board linked to this team. Link a board in team setup first.");
        }

        trelloPlanPublisher.publishSprint(normalizedSprintId, team.getTrelloBoardId(), userToken);
        return ResponseEntity.ok("Sprint pushed to Trello successfully");
    }

    @DeleteMapping("/cleanup")
    @Transactional
    public ResponseEntity<String> cleanupDeadSprints() {
        if (!SecurityUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
        }
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Sprint> staleSprints = sprintRepository.findStalePlanningSprintsBefore(cutoff);
        for (Sprint sprint : staleSprints) {
            sprintRepository.deleteById(sprint.getId());
        }
        return ResponseEntity.ok("Deleted " + staleSprints.size() + " sprints");
    }

    @GetMapping("/{sprintId}/review")
    public ResponseEntity<SprintReviewSummary> getSprintReview(@PathVariable String sprintId) {
        UUID normalizedSprintId = normalizeSprintId(sprintId);
        Sprint sprint = accessGuard.requireSprint(normalizedSprintId);

        com.sprintplanner.domain.model.SprintReview review = sprint.getSprintReview();
        if (review == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new SprintReviewSummary(
            review.getId(),
            review.getGoalResult() != null ? review.getGoalResult().name() : null,
            review.getBlockerNote(),
            review.getTotalPlannedPts(),
            review.getTotalCompletedPts(),
            review.getSubmittedAt()
        ));
    }

    @GetMapping("/{sprintId}/retrospective")
    public ResponseEntity<RetrospectiveReport> getRetrospective(@PathVariable String sprintId) {
        UUID normalizedId = normalizeSprintId(sprintId);
        Sprint sprint = accessGuard.requireSprint(normalizedId);

        com.sprintplanner.domain.model.SprintReview review = sprint.getSprintReview();
        if (review == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Serve from cache if available
        if (review.getRetrospectiveReport() != null && !review.getRetrospectiveReport().isBlank()) {
            try {
                RetrospectiveReport cached = objectMapper.readValue(
                    review.getRetrospectiveReport(), RetrospectiveReport.class);
                return ResponseEntity.ok(cached);
            } catch (Exception e) {
                logger.warn("[RETROSPECTIVE] Cache deserialization failed for sprint {}, regenerating: {}", normalizedId, e.getMessage());
            }
        }

        aiUsageLimiter.consume(SecurityUtils.getCurrentUserId(), SecurityUtils.isAdmin(), AiUsageLimiter.WEIGHT_SINGLE);
        // On-demand generation — then cache the result so subsequent calls are instant
        RetrospectiveReport report = retrospectiveService.generateReport(normalizedId);
        try {
            String json = objectMapper.writeValueAsString(report);
            sprintReviewService.cacheRetrospectiveReport(normalizedId, json);
        } catch (Exception e) {
            logger.warn("[RETROSPECTIVE] Failed to cache after on-demand generation for sprint {}: {}", normalizedId, e.getMessage());
        }
        return ResponseEntity.ok(report);
    }

    @DeleteMapping("/{sprintId}")
    public ResponseEntity<Void> deleteSprint(@PathVariable String sprintId) {
        UUID normalizedSprintId = normalizeSprintId(sprintId);
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Sprint sprint = sprintRepository.findById(normalizedSprintId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found: " + normalizedSprintId));

        teamRepository.findById(sprint.getTeamId())
            .filter(team -> currentUserId.equals(team.getOwnerId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        if (sprint.getStatus() == Sprint.Status.ACTIVE || sprint.getStatus() == Sprint.Status.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete an active or completed sprint");
        }

        sprintRepository.deleteById(normalizedSprintId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{sprintId}/tasks/{taskId}/guidance")
    public ResponseEntity<TaskGuidanceResponse> getTaskGuidance(
            @PathVariable String sprintId,
            @PathVariable UUID taskId) {
        UUID normalizedSprintId = normalizeSprintId(sprintId);

        Sprint sprint = accessGuard.requireSprint(normalizedSprintId);
        Task task = accessGuard.requireTaskInSprint(taskId, normalizedSprintId);
        aiUsageLimiter.consume(SecurityUtils.getCurrentUserId(), SecurityUtils.isAdmin(), AiUsageLimiter.WEIGHT_SINGLE);

        String teamProfileMemory = memoryReader.readTeamProfile(sprint.getTeamId());
        String sprintHistoryMemory = memoryReader.readSprintHistory(sprint.getTeamId());

        String devName = "Unassigned";
        if (task.getAssignedDevId() != null) {
            devName = developerRepository.findById(task.getAssignedDevId())
                .map(Developer::getName).orElse("Unknown");
        }

        String prompt = buildGuidancePrompt(task, devName, teamProfileMemory, sprintHistoryMemory, sprint.getCodebaseContext());
        String raw = geminiClientProvider.generate(prompt, 0.3f, GeminiClientProvider.MODEL_ESTIMATION);

        try {
            String cleaned = raw.trim()
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();
            JsonNode node = objectMapper.readTree(cleaned);
            String hints = node.path("hints").asText("");
            String suggestedApproach = node.path("suggestedApproach").asText("");
            List<String> riskFlags = new ArrayList<>();
            for (JsonNode flag : node.path("riskFlags")) {
                riskFlags.add(flag.asText());
            }
            return ResponseEntity.ok(new TaskGuidanceResponse(hints, suggestedApproach, riskFlags));
        } catch (Exception e) {
            return ResponseEntity.ok(new TaskGuidanceResponse(raw, "", List.of()));
        }
    }

    private String buildGuidancePrompt(Task task, String devName, String teamProfile, String sprintHistory, String codebaseContext) {
        String codebaseSection = (codebaseContext != null && !codebaseContext.isBlank())
            ? "\nCodebase Context:\n" + codebaseContext + "\n"
            : "";
        return """
            You are a senior software engineer giving implementation advice.

            Task: %s
            Description: %s
            Acceptance Criteria: %s
            Role: %s
            Story Points: %d
            Assigned to: %s

            Team Preferences:
            %s

            Sprint History:
            %s
            %s
            Provide practical guidance tailored to this specific tech stack and codebase state:
            1. 2-3 concrete implementation hints specific to this task
            2. Risk flags: patterns from history suggesting this task type is often underestimated or problematic
            3. A suggested approach in 2-4 numbered steps

            Respond ONLY with JSON:
            {
              "hints": "string — 2-3 sentences of implementation advice",
              "suggestedApproach": "string — numbered steps as plain text",
              "riskFlags": ["string", "string"]
            }
            """.formatted(
                task.getTitle(), task.getDescription(), task.getAcceptanceCriterion(),
                task.getRoleTag(), task.getStoryPoints(), devName,
                teamProfile != null ? teamProfile : "No preferences yet",
                sprintHistory != null ? sprintHistory : "No history yet",
                codebaseSection
            );
    }

    @PostMapping("/parse-context")
    public ResponseEntity<Map<String, String>> parseContext(
            @RequestParam("file") MultipartFile file) {

        String filename = file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase();

        try {
            String text;
            if (filename.endsWith(".pdf")) {
                text = extractPdf(file.getBytes());
            } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                text = extractExcel(file.getInputStream());
            } else if (filename.endsWith(".txt") || filename.endsWith(".md")) {
                text = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Unsupported file type. Use .pdf, .xlsx, .xls, .txt, or .md"));
            }

            if (text == null || text.isBlank()) {
                String msg = filename.endsWith(".pdf")
                        ? "This PDF appears to be scanned — no text layer found. Paste the content manually instead."
                        : "Could not extract text from file — paste the content manually instead.";
                return ResponseEntity.badRequest().body(Map.of("error", msg));
            }

            return ResponseEntity.ok(Map.of("text", text.trim()));

        } catch (Exception e) {
            logger.warn("[PARSE-CONTEXT] Extraction failed for {}: {}", filename, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Extraction failed: " + e.getMessage()));
        }
    }

    private String extractPdf(byte[] bytes) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                org.apache.pdfbox.Loader.loadPDF(bytes)) {
            org.apache.pdfbox.text.PDFTextStripper stripper =
                    new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String extractExcel(java.io.InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (org.apache.poi.ss.usermodel.Workbook workbook =
                org.apache.poi.ss.usermodel.WorkbookFactory.create(stream)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(i);
                sb.append("Sheet: ").append(sheet.getSheetName()).append("\n");
                for (org.apache.poi.ss.usermodel.Row row : sheet) {
                    StringBuilder rowText = new StringBuilder();
                    for (org.apache.poi.ss.usermodel.Cell cell : row) {
                        String val = switch (cell.getCellType()) {
                            case STRING -> cell.getStringCellValue();
                            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
                            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                            default -> "";
                        };
                        if (!val.isBlank()) {
                            rowText.append(val).append("\t");
                        }
                    }
                    if (!rowText.isEmpty()) {
                        sb.append(rowText.toString().trim()).append("\n");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /** Caps input size so one request cannot burn a huge number of AI tokens. */
    private void validatePlanInput(PlanSprintHttpRequest request) {
        if (request.sprintGoal() != null && request.sprintGoal().length() > 1_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sprint goal is too long (max 1000 characters).");
        }
        if (request.epics() != null) {
            if (request.epics().size() > 15) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many epics (max 15 per sprint).");
            }
            for (var epic : request.epics()) {
                if ((epic.title() != null && epic.title().length() > 300)
                        || (epic.description() != null && epic.description().length() > 5_000)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An epic title or description is too long.");
                }
            }
        }
        if ((request.codebaseContext() != null && request.codebaseContext().length() > 30_000)
                || (request.previousSprintContext() != null && request.previousSprintContext().length() > 30_000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imported context is too long (max 30000 characters).");
        }
        if (request.carryoverTasks() != null && request.carryoverTasks().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many carry-over tasks.");
        }
    }

    private UUID normalizeSprintId(String rawSprintId) {
        if (rawSprintId == null || rawSprintId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sprintId path variable is required.");
        }

        String trimmed = rawSprintId.trim();
        Matcher matcher = UUID_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return UUID.fromString(matcher.group());
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sprintId: " + rawSprintId);
    }
}
