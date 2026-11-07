package com.sprintplanner.web;

import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.Team;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.domain.port.out.TeamRepository;
import com.sprintplanner.infrastructure.persistence.TaskJpaRepository;
import com.sprintplanner.infrastructure.persistence.UserStoryJpaRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Central tenant-isolation checks. A non-admin user may only touch teams they own,
 * and the sprints / tasks that belong to those teams.
 *
 * Failures are reported as 404 (not 403) so IDs of other users' resources cannot be probed.
 */
@Component
public class AccessGuard {

    private final TeamRepository teamRepository;
    private final SprintRepository sprintRepository;
    private final TaskJpaRepository taskRepository;
    private final UserStoryJpaRepository storyRepository;

    public AccessGuard(
        TeamRepository teamRepository,
        SprintRepository sprintRepository,
        TaskJpaRepository taskRepository,
        UserStoryJpaRepository storyRepository
    ) {
        this.teamRepository = teamRepository;
        this.sprintRepository = sprintRepository;
        this.taskRepository = taskRepository;
        this.storyRepository = storyRepository;
    }

    public Team requireTeam(UUID teamId) {
        if (teamId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "teamId is required.");
        }
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        if (!SecurityUtils.isAdmin() && !SecurityUtils.getCurrentUserId().equals(team.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found");
        }
        return team;
    }

    public Sprint requireSprint(UUID sprintId) {
        if (sprintId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sprintId is required.");
        }
        Sprint sprint = sprintRepository.findById(sprintId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
        requireTeam(sprint.getTeamId());
        return sprint;
    }

    /** Loads the task and verifies it belongs to the given (already authorised) sprint. */
    public Task requireTaskInSprint(UUID taskId, UUID sprintId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        boolean inSprint = task.getStoryId() != null && storyRepository.findById(task.getStoryId())
            .map(story -> sprintId.equals(story.getSprintId()))
            .orElse(false);
        if (!inSprint) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return task;
    }
}
