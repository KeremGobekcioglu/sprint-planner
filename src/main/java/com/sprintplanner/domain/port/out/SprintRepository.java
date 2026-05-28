package com.sprintplanner.domain.port.out;

import com.sprintplanner.domain.model.Sprint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for sprint aggregate state.
 */
public interface SprintRepository {

    Sprint save(Sprint sprint);

    Optional<Sprint> findById(UUID sprintId);

    Optional<Sprint> findWithStoriesAndTasksById(UUID sprintId);

    List<Sprint> findByTeamId(UUID teamId);

    List<Sprint> findByTeamIdWithReviews(UUID teamId);

    long countCompletedByTeamId(UUID teamId);

    List<Sprint> findActiveForTeams(List<UUID> teamIds);

    List<Sprint> findActiveByTeamIds(List<UUID> teamIds);

    List<Sprint> findRecentCompletedByTeamIds(List<UUID> teamIds, int limit);

    void deleteById(UUID sprintId);

    List<Sprint> findStalePlanningSprintsBefore(Instant cutoff);
}
