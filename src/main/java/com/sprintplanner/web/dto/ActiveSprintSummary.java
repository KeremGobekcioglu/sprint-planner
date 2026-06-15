package com.sprintplanner.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ActiveSprintSummary(
    UUID sprintId,
    UUID teamId,
    String teamName,
    String teamDomain,
    String sprintGoal,
    String status,
    Instant plannedAt,
    int storyCount,
    int totalPoints
) {}
