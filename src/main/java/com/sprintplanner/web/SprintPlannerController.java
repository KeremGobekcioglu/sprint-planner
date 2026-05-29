package com.sprintplanner.web;

import com.sprintplanner.application.PreferenceService;
import com.sprintplanner.domain.dto.Epic;
import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.dto.SprintPlanMapper;
import com.sprintplanner.domain.dto.UserStoryDto;
import com.sprintplanner.domain.model.AppUser;
import com.sprintplanner.domain.model.EditLog;
import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.Team;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.in.PlanningInputPort;
import com.sprintplanner.domain.port.out.EditLogRepository;
import com.sprintplanner.domain.port.out.MemoryReader;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.domain.port.out.TeamRepository;
import com.sprintplanner.infrastructure.persistence.JpaUserRepository;
import com.sprintplanner.infrastructure.progress.PlanningProgressStore;
import com.sprintplanner.infrastructure.publisher.TrelloPlanPublisher;
import com.sprintplanner.web.dto.LogEditHttpRequest;
import com.sprintplanner.web.dto.PlanSprintHttpRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sprints")
public class SprintPlannerController {

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
    );

    private final PlanningInputPort planningInputPort;
    private final SprintRepository sprintRepository;
    private final MemoryReader memoryReader;
    private final EditLogRepository editLogRepository;
    private final TeamRepository teamRepository;
    private final PreferenceService preferenceService;
    private final PlanningProgressStore progressStore;
    private final TrelloPlanPublisher trelloPlanPublisher;
    private final JpaUserRepository userRepository;

    public SprintPlannerController(PlanningInputPort planningInputPort, SprintRepository sprintRepository,
            MemoryReader memoryReader, EditLogRepository editLogRepository, TeamRepository teamRepository,
            PreferenceService preferenceService, PlanningProgressStore progressStore,
            TrelloPlanPublisher trelloPlanPublisher, JpaUserRepository userRepository) {
        this.planningInputPort = planningInputPort;
        this.sprintRepository = sprintRepository;
        this.memoryReader = memoryReader;
        this.editLogRepository = editLogRepository;
        this.teamRepository = teamRepository;
        this.preferenceService = preferenceService;
        this.progressStore = progressStore;
        this.trelloPlanPublisher = trelloPlanPublisher;
        this.userRepository = userRepository;
    }

    @PostMapping("/plan")
    public ResponseEntity<SprintPlan> planSprint(@RequestBody PlanSprintHttpRequest request) {
        UUID currentUserId = SecurityUtils .getCurrentUserId();
        PlanningRequest planningRequest = new PlanningRequest(
            request.teamId(),
            currentUserId,
            request.epics() == null
                ? List.of()
                : request.epics().stream().map(epic -> new Epic(epic.title(), epic.description())).toList(),
            request.domain(),
            request.sprintGoal(),
            request.daysOff() == null ? Map.of() : request.daysOff(),
            request.previousSprintContext()
        );
        
        SprintPlan plan = planningInputPort.planSprint(planningRequest);
        
        // Create and save Sprint entity to database
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint(
            sprintId,
            request.teamId(),
            1,  // sprintNumber - in real app, would query max sprintNumber
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

        sprint.setUserStories(userStories);
        sprintRepository.save(sprint);

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
                "PLANNING"
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

    @GetMapping("/teams/{teamId}/planning-mode")
    public ResponseEntity<Map<String, Object>> getPlanningMode(@PathVariable UUID teamId) {
        long completedSprints = sprintRepository.countCompletedByTeamId(teamId);
        String teamProfile = memoryReader.readTeamProfile(teamId);
        String sprintHistory = memoryReader.readSprintHistory(teamId);

        Map<String, Object> body = Map.of(
            "mode", completedSprints > 0 ? "CONTINUOUS" : "FRESH",
            "completedSprints", completedSprints,
            "hasTeamProfile", teamProfile != null && !teamProfile.isBlank(),
            "hasSprintHistory", sprintHistory != null && !sprintHistory.isBlank()
        );

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{sprintId}/plan")
    public ResponseEntity<SprintPlan> getSprintPlan(@PathVariable String sprintId) {
        UUID normalizedSprintId = normalizeSprintId(sprintId);
        Sprint sprint = sprintRepository.findWithStoriesAndTasksById(normalizedSprintId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found: " + normalizedSprintId));
        
        SprintPlan plan = SprintPlanMapper.toSprintPlan(sprint);
        return ResponseEntity.ok(plan);
    }

    @PostMapping("/{sprintId}/approve")
    public ResponseEntity<String> approveSprint(@PathVariable String sprintId) {
        UUID normalizedSprintId = normalizeSprintId(sprintId);
        Sprint sprint = sprintRepository.findById(normalizedSprintId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found: " + normalizedSprintId));
        
        sprint.setStatus(Sprint.Status.ACTIVE);
        sprint.setReviewedAt(Instant.now());
        sprintRepository.save(sprint);

        // Rebuild team_profile.md from accumulated edit logs
        var editLogs = editLogRepository.findBySprintId(normalizedSprintId);
        preferenceService.updateTeamProfile(sprint.getTeamId(), editLogs);

        return ResponseEntity.ok("Sprint approved and moved to ACTIVE.");
    }

    @PostMapping("/{sprintId}/edits")
    public ResponseEntity<String> logEdit(@PathVariable String sprintId, @RequestBody LogEditHttpRequest request) {
        UUID normalizedSprintId = normalizeSprintId(sprintId);
        EditLog log = new EditLog(
            UUID.randomUUID(),
            normalizedSprintId,
            request.field(),
            request.oldValue(),
            request.newValue(),
            request.taskType(),
            Instant.now()
        );
        editLogRepository.save(log);
        return ResponseEntity.ok("Edit logged.");
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

        Sprint sprint = sprintRepository.findById(normalizedSprintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));

        Team team = teamRepository.findById(sprint.getTeamId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

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

    @DeleteMapping("/{sprintId}")
    public ResponseEntity<Void> deleteSprint(@PathVariable String sprintId) {
        UUID normalizedSprintId = normalizeSprintId(sprintId);
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Sprint sprint = sprintRepository.findById(normalizedSprintId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found: " + normalizedSprintId));

        teamRepository.findById(sprint.getTeamId())
            .filter(team -> team.getOwnerId().equals(currentUserId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        if (sprint.getStatus() == Sprint.Status.ACTIVE || sprint.getStatus() == Sprint.Status.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete an active or completed sprint");
        }

        sprintRepository.deleteById(normalizedSprintId);
        return ResponseEntity.noContent().build();
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
