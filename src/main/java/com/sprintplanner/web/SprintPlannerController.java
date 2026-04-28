package com.sprintplanner.web;

import com.sprintplanner.domain.dto.Epic;
import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.model.EditLog;
import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.in.PlanningInputPort;
import com.sprintplanner.domain.port.out.EditLogRepository;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.web.dto.LogEditHttpRequest;
import com.sprintplanner.web.dto.PlanSprintHttpRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sprints")
public class SprintPlannerController {

    private final PlanningInputPort planningInputPort;
    private final SprintRepository sprintRepository;
    private final EditLogRepository editLogRepository;

    public SprintPlannerController(PlanningInputPort planningInputPort, SprintRepository sprintRepository, EditLogRepository editLogRepository) {
        this.planningInputPort = planningInputPort;
        this.sprintRepository = sprintRepository;
        this.editLogRepository = editLogRepository;
    }

    @PostMapping("/plan")
    public ResponseEntity<SprintPlan> planSprint(@RequestBody PlanSprintHttpRequest request) {
        PlanningRequest planningRequest = new PlanningRequest(
            request.teamId(),
            request.epics() == null
                ? List.of()
                : request.epics().stream().map(epic -> new Epic(epic.title(), epic.description())).toList(),
            request.domain(),
            request.sprintGoal(),
            request.daysOff() == null ? Map.of() : request.daysOff()
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
        
        // Set sprint ID on all user stories before persisting
        if (plan.userStories() != null) {
            for (UserStory story : plan.userStories()) {
                story.setSprintId(sprintId);
            }
        }
        
        sprint.setUserStories(plan.userStories());
        sprintRepository.save(sprint);
        
        return ResponseEntity.ok(plan);
    }

    @GetMapping("/{sprintId}/plan")
    public ResponseEntity<SprintPlan> getSprintPlan(@PathVariable UUID sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId)
            .orElseThrow(() -> new IllegalArgumentException("Sprint not found: " + sprintId));
        
        SprintPlan plan = new SprintPlan(
            sprint.getTeamId(),
            sprint.getGoal(),
            sprint.getUserStories()
        );
        return ResponseEntity.ok(plan);
    }

    @PostMapping("/{sprintId}/approve")
    public ResponseEntity<String> approveSprint(@PathVariable UUID sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId)
            .orElseThrow(() -> new IllegalArgumentException("Sprint not found: " + sprintId));
        
        sprint.setStatus(Sprint.Status.ACTIVE);
        sprint.setReviewedAt(Instant.now());
        sprintRepository.save(sprint);
        
        return ResponseEntity.ok("Sprint approved and moved to ACTIVE.");
    }

    @PostMapping("/{sprintId}/edits")
    public ResponseEntity<String> logEdit(@PathVariable UUID sprintId, @RequestBody LogEditHttpRequest request) {
        EditLog log = new EditLog(
            UUID.randomUUID(),
            sprintId,
            request.field(),
            request.oldValue(),
            request.newValue(),
            request.taskType(),
            Instant.now()
        );
        editLogRepository.save(log);
        return ResponseEntity.ok("Edit logged.");
    }
}
