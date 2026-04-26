package com.sprintplanner.application;

import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Team;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.TeamRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final DeveloperRepository developerRepository;

    public TeamService(TeamRepository teamRepository, DeveloperRepository developerRepository) {
        this.teamRepository = teamRepository;
        this.developerRepository = developerRepository;
    }

    public Team createTeam(String name, String domain, int sprintLengthDays, String trelloBoardId) {
        Team team = new Team(
            UUID.randomUUID(),
            name,
            domain,
            sprintLengthDays,
            trelloBoardId,
            Instant.now()
        );
        return teamRepository.save(team);
    }

    public Team getTeam(UUID teamId) {
        return teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
    }

    public Developer addDeveloper(UUID teamId, String name, String role, int baseVelocity) {
        Team team = getTeam(teamId);
        Developer developer = new Developer(UUID.randomUUID(), team.getId(), name, role, baseVelocity);
        return developerRepository.save(developer);
    }

    public List<Developer> getDevelopers(UUID teamId) {
        return developerRepository.findByTeamId(teamId);
    }
}
