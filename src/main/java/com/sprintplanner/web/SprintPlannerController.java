package com.sprintplanner.web;

import com.sprintplanner.domain.dto.Epic;
import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.port.in.PlanningInputPort;
import com.sprintplanner.web.dto.PlanSprintHttpRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planning")
public class SprintPlannerController {

    private final PlanningInputPort planningInputPort;

    public SprintPlannerController(PlanningInputPort planningInputPort) {
        this.planningInputPort = planningInputPort;
    }

    @PostMapping("/sprints/plan")
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
        return ResponseEntity.ok(planningInputPort.planSprint(planningRequest));
    }
}
