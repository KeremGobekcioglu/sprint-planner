package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.Sprint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintJpaRepository extends JpaRepository<Sprint, UUID> {

    @EntityGraph(value = "Sprint.withUserStoriesAndTasks")
    Optional<Sprint> findWithStoriesAndTasksById(UUID id);

    List<Sprint> findByTeamId(UUID teamId);
}
