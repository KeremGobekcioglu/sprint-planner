package com.sprintplanner.web;

import com.sprintplanner.application.TeamService;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.port.out.MemoryReader;
import com.sprintplanner.domain.port.out.MemoryWriter;
import com.sprintplanner.domain.port.out.TeamRepository;
import com.sprintplanner.web.dto.AddDeveloperHttpRequest;
import com.sprintplanner.web.dto.CreateTeamHttpRequest;
import com.sprintplanner.web.dto.DeveloperHttpDto;
import com.sprintplanner.web.dto.TeamHttpDto;
import com.sprintplanner.web.dto.TeamMemoryResponse;
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
    private final TeamRepository teamRepository;
    private final MemoryReader memoryReader;
    private final MemoryWriter memoryWriter;

    public TeamController(TeamService teamService, TeamRepository teamRepository, MemoryReader memoryReader, MemoryWriter memoryWriter) {
        this.teamService = teamService;
        this.teamRepository = teamRepository;
        this.memoryReader = memoryReader;
        this.memoryWriter = memoryWriter;
    }

    @GetMapping
    public ResponseEntity<List<TeamHttpDto>> getTeams() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        List<TeamHttpDto> teams = teamService.getTeams(currentUserId, isAdmin).stream().map(TeamHttpDto::from).toList();
        return ResponseEntity.ok(teams);
    }

    @PostMapping
    public ResponseEntity<TeamHttpDto> createTeam(@RequestBody CreateTeamHttpRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
            TeamHttpDto.from(
                teamService.createTeam(
                    request.name(),
                    request.domain(),
                    request.sprintLengthDays(),
                    request.trelloBoardId(),
                    currentUserId
                )
            )
        );
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamHttpDto> getTeam(@PathVariable UUID teamId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        return ResponseEntity.ok(TeamHttpDto.from(teamService.getTeam(teamId, currentUserId, isAdmin)));
    }

    @GetMapping("/{teamId}/developers")
    public ResponseEntity<List<DeveloperHttpDto>> getDevelopers(@PathVariable UUID teamId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        List<DeveloperHttpDto> developers = teamService.getDevelopers(teamId, currentUserId, isAdmin)
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
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        Developer developer = teamService.addDeveloper(
            teamId,
            currentUserId,
            isAdmin,
            request.name(),
            request.role(),
            request.baseVelocity()
        );
        return ResponseEntity.ok(DeveloperHttpDto.from(developer));
    }

    @DeleteMapping("/{teamId}/developers/{developerId}")
    public ResponseEntity<Void> removeDeveloper(@PathVariable UUID teamId, @PathVariable UUID developerId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        teamService.removeDeveloper(teamId, currentUserId, isAdmin, developerId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{teamId}/memory")
    public ResponseEntity<TeamMemoryResponse> getTeamMemory(@PathVariable UUID teamId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        var team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        if (!isAdmin && (team.getOwnerId() == null || !team.getOwnerId().equals(currentUserId))) {
            throw new IllegalArgumentException("Team not found: " + teamId);
        }

        String teamProfile = memoryReader.readTeamProfile(teamId);
        String sprintHistory = memoryReader.readSprintHistory(teamId);

        TeamMemoryResponse response = new TeamMemoryResponse(
            teamProfile.isEmpty() ? null : teamProfile,
            sprintHistory.isEmpty() ? null : sprintHistory,
            null,
            null
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{teamId}/memory/profile")
    public ResponseEntity<Void> clearTeamProfile(@PathVariable UUID teamId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        var team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        if (!isAdmin && (team.getOwnerId() == null || !team.getOwnerId().equals(currentUserId))) {
            throw new IllegalArgumentException("Team not found: " + teamId);
        }

        memoryWriter.writeTeamProfile(teamId, "");
        return ResponseEntity.noContent().build();
    }
}
