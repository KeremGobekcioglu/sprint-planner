package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.port.out.SprintRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSprintRepository implements SprintRepository {

    private final SprintJpaRepository sprintJpaRepository;

    public JpaSprintRepository(SprintJpaRepository sprintJpaRepository) {
        this.sprintJpaRepository = sprintJpaRepository;
    }

    @Override
    public Sprint save(Sprint sprint) {
        return sprintJpaRepository.save(sprint);
    }

    @Override
    public Optional<Sprint> findById(UUID sprintId) {
        return sprintJpaRepository.findById(sprintId);
    }

    @Override
    public List<Sprint> findByTeamId(UUID teamId) {
        return sprintJpaRepository.findByTeamId(teamId);
    }
}
