package com.sprintplanner.web;

import com.sprintplanner.application.TeamService;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.web.dto.AddDeveloperHttpRequest;
import com.sprintplanner.web.dto.CreateTeamHttpRequest;
import com.sprintplanner.web.dto.DeveloperHttpDto;
import com.sprintplanner.web.dto.TeamHttpDto;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @GetMapping
    public ResponseEntity<List<TeamHttpDto>> getTeams() {
        List<TeamHttpDto> teams = teamService.getTeams().stream().map(TeamHttpDto::from).toList();
        return ResponseEntity.ok(teams);
    }

    @PostMapping
    public ResponseEntity<TeamHttpDto> createTeam(@RequestBody CreateTeamHttpRequest request) {
        return ResponseEntity.ok(
            TeamHttpDto.from(
                teamService.createTeam(
            request.name(),
            request.domain(),
            request.sprintLengthDays(),
            request.trelloBoardId()
                )
            )
        );
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamHttpDto> getTeam(@PathVariable UUID teamId) {
        return ResponseEntity.ok(TeamHttpDto.from(teamService.getTeam(teamId)));
    }

    @GetMapping("/{teamId}/developers")
    public ResponseEntity<List<DeveloperHttpDto>> getDevelopers(@PathVariable UUID teamId) {
        List<DeveloperHttpDto> developers = teamService.getDevelopers(teamId)
            .stream()
            .map(DeveloperHttpDto::from)
            .toList();
        return ResponseEntity.ok(developers);
    }

    @PostMapping("/{teamId}/developers")
    public ResponseEntity<DeveloperHttpDto> addDeveloper(
        @PathVariable UUID teamId,
        @RequestBody AddDeveloperHttpRequest request
    ) {
        Developer developer = teamService.addDeveloper(
            teamId,
            request.name(),
            request.role(),
            request.baseVelocity()
        );
        return ResponseEntity.ok(DeveloperHttpDto.from(developer));
    }

    @DeleteMapping("/{teamId}/developers/{developerId}")
    public ResponseEntity<Void> removeDeveloper(@PathVariable UUID teamId, @PathVariable UUID developerId) {
        teamService.removeDeveloper(teamId, developerId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
