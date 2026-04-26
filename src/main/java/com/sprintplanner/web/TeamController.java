package com.sprintplanner.web;

import com.sprintplanner.application.TeamService;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Team;
import com.sprintplanner.web.dto.AddDeveloperHttpRequest;
import com.sprintplanner.web.dto.CreateTeamHttpRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ResponseEntity<Team> createTeam(@RequestBody CreateTeamHttpRequest request) {
        Team team = teamService.createTeam(
            request.name(),
            request.domain(),
            request.sprintLengthDays(),
            request.trelloBoardId()
        );
        return ResponseEntity.ok(team);
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<Team> getTeam(@PathVariable UUID teamId) {
        return ResponseEntity.ok(teamService.getTeam(teamId));
    }

    @GetMapping("/{teamId}/developers")
    public ResponseEntity<List<Developer>> getDevelopers(@PathVariable UUID teamId) {
        return ResponseEntity.ok(teamService.getDevelopers(teamId));
    }

    @PostMapping("/{teamId}/developers")
    public ResponseEntity<Developer> addDeveloper(
        @PathVariable UUID teamId,
        @RequestBody AddDeveloperHttpRequest request
    ) {
        Developer developer = teamService.addDeveloper(
            teamId,
            request.name(),
            request.role(),
            request.baseVelocity()
        );
        return ResponseEntity.ok(developer);
    }
}
