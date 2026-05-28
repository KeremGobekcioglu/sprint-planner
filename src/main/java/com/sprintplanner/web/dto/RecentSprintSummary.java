package com.sprintplanner.web.dto;

import java.time.Instant;
import java.util.UUID;

public record RecentSprintSummary(
    UUID sprintId,
    UUID teamId,
    String teamName,
    String sprintGoal,
    String goalResult,
    int plannedPts,
    int completedPts,
    Instant submittedAt
) {}
