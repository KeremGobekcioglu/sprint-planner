package com.sprintplanner.domain.dto;

import java.util.List;
import java.util.UUID;

/**
 * The final output object returned by the planning pipeline.
 * Uses lightweight UserStoryDto to break circular references during serialization.
 */
public record SprintPlan(
    UUID teamId,
    String sprintGoal,
    List<UserStoryDto> userStories,
    List<TaskDto> unassignedTasks,
    String sprintStatus
) {}
