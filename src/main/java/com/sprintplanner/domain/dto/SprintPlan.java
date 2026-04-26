package com.sprintplanner.domain.dto;

import com.sprintplanner.domain.model.UserStory;
import java.util.List;
import java.util.UUID;

/**
 * The final output object returned by the planning pipeline.
 */
public record SprintPlan(
    UUID teamId,
    String sprintGoal,
    List<UserStory> userStories
) {}
