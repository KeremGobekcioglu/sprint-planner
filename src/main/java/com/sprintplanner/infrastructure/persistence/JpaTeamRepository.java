package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.Team;
import com.sprintplanner.domain.port.out.TeamRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaTeamRepository implements TeamRepository {

    private final TeamJpaRepository teamJpaRepository;

    public JpaTeamRepository(TeamJpaRepository teamJpaRepository) {
        this.teamJpaRepository = teamJpaRepository;
    }

    @Override
    public Team save(Team team) {
        return teamJpaRepository.save(team);
    }

    @Override
    public Optional<Team> findById(UUID teamId) {
        return teamJpaRepository.findById(teamId);
    }

    @Override
    public List<Team> findAll() {
        return teamJpaRepository.findAll();
    }

    @Override
    public List<Team> findByOwnerId(UUID ownerId) {
        return teamJpaRepository.findByOwnerId(ownerId);
    }
}
