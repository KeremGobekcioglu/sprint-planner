package com.sprintplanner.application;

import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Team;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.TeamRepository;
import com.sprintplanner.domain.service.CapabilityInferenceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final DeveloperRepository developerRepository;

    public TeamService(TeamRepository teamRepository, DeveloperRepository developerRepository) {
        this.teamRepository = teamRepository;
        this.developerRepository = developerRepository;
    }

    public Team createTeam(String name, String domain, int sprintLengthDays, String trelloBoardId, UUID ownerId) {
        Team team = new Team(
            UUID.randomUUID(),
            name,
            domain,
            sprintLengthDays,
            ownerId,
            trelloBoardId,
            Instant.now()
        );
        return teamRepository.save(team);
    }

    public Team getTeam(UUID teamId, UUID currentUserId, boolean isAdmin) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        assertCanAccessTeam(team, currentUserId, isAdmin);
        return team;
    }

    public List<Team> getTeams(UUID currentUserId, boolean isAdmin) {
        return isAdmin ? teamRepository.findAll() : teamRepository.findByOwnerId(currentUserId);
    }

    public Developer addDeveloper(UUID teamId, UUID currentUserId, boolean isAdmin, String name, String role, List<String> capabilities, int baseVelocity) {
        Team team = getTeam(teamId, currentUserId, isAdmin);
        Developer developer = new Developer(UUID.randomUUID(), team.getId(), name, role, baseVelocity);
        List<String> resolvedCapabilities = (capabilities == null || capabilities.isEmpty())
            ? CapabilityInferenceService.inferFromRole(role)
            : capabilities;
        developer.setCapabilities(resolvedCapabilities);
        return developerRepository.save(developer);
    }

    public List<Developer> getDevelopers(UUID teamId, UUID currentUserId, boolean isAdmin) {
        getTeam(teamId, currentUserId, isAdmin);
        return developerRepository.findByTeamId(teamId);
    }

    @Transactional
    public void removeDeveloper(UUID teamId, UUID currentUserId, boolean isAdmin, UUID developerId) {
        getTeam(teamId, currentUserId, isAdmin);
        boolean deleted = developerRepository.deleteByIdAndTeamId(developerId, teamId);
        if (!deleted) {
            throw new IllegalArgumentException(
                "Developer not found for team. teamId=" + teamId + ", developerId=" + developerId
            );
        }
    }

    private void assertCanAccessTeam(Team team, UUID currentUserId, boolean isAdmin) {
        if (isAdmin || currentUserId == null) {
            return;
        }

        if (team.getOwnerId() == null || !team.getOwnerId().equals(currentUserId)) {
            throw new IllegalArgumentException("Team not found: " + team.getId());
        }
    }

}
