package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.port.out.SprintRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
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
    public Optional<Sprint> findWithStoriesAndTasksById(UUID sprintId) {
        return sprintJpaRepository.findWithStoriesAndTasksById(sprintId);
    }

    @Override
    public List<Sprint> findByTeamId(UUID teamId) {
        return sprintJpaRepository.findByTeamId(teamId);
    }

    @Override
    public List<Sprint> findByTeamIdWithReviews(UUID teamId) {
        return sprintJpaRepository.findByTeamIdWithReviews(teamId);
    }

    @Override
    public long countCompletedByTeamId(UUID teamId) {
        return sprintJpaRepository.countByTeamIdAndStatus(teamId, Sprint.Status.COMPLETED);
    }

    @Override
    public List<Sprint> findActiveForTeams(List<UUID> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return List.of();
        }
        List<Sprint.Status> activeStatuses = Arrays.asList(
            Sprint.Status.PLANNING,
            Sprint.Status.IN_REVIEW,
            Sprint.Status.ACTIVE
        );
        return sprintJpaRepository.findByTeamIdInAndStatusIn(teamIds, activeStatuses);
    }

    @Override
    public List<Sprint> findActiveByTeamIds(List<UUID> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return List.of();
        }
        return sprintJpaRepository.findActiveByTeamIdIn(teamIds, Arrays.asList(
            Sprint.Status.PLANNING, Sprint.Status.IN_REVIEW, Sprint.Status.ACTIVE));
    }

    @Override
    public List<Sprint> findRecentCompletedByTeamIds(List<UUID> teamIds, int limit) {
        if (teamIds == null || teamIds.isEmpty()) {
            return List.of();
        }
        return sprintJpaRepository.findRecentCompletedByTeamIdIn(
            teamIds, Sprint.Status.COMPLETED, PageRequest.of(0, limit));
    }

    @Override
    public void deleteById(UUID sprintId) {
        sprintJpaRepository.deleteById(sprintId);
    }

    @Override
    public List<Sprint> findStalePlanningSprintsBefore(Instant cutoff) {
        return sprintJpaRepository.findByStatusAndPlannedAtBefore(Sprint.Status.PLANNING, cutoff);
    }
}
