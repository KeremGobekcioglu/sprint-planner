package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.Sprint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SprintJpaRepository extends JpaRepository<Sprint, UUID> {

    @EntityGraph(value = "Sprint.withUserStoriesAndTasks")
    Optional<Sprint> findWithStoriesAndTasksById(UUID id);

    List<Sprint> findByTeamId(UUID teamId);

    long countByTeamIdAndStatus(UUID teamId, Sprint.Status status);

    List<Sprint> findByTeamIdInAndStatusIn(List<UUID> teamIds, List<Sprint.Status> statuses);

    @Query("SELECT s FROM Sprint s LEFT JOIN FETCH s.sprintReview sr LEFT JOIN FETCH sr.devCompletions WHERE s.teamId = :teamId")
    List<Sprint> findByTeamIdWithReviews(@Param("teamId") UUID teamId);
}
