package com.sprintplanner.domain.dto;

import java.util.UUID;

public record CarryoverTaskDto(
    UUID taskId,
    UUID sprintId,
    String sprintGoal,
    String epicTitle,
    String storyTitle,
    String taskTitle,
    String description,
    String acceptanceCriterion,
    String roleTag,
    int storyPoints,
    UUID previousAssignedDevId,
    String previousAssignedDevName
) {}
