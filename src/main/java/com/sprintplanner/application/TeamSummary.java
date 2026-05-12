package com.sprintplanner.application;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Summary row for a team shown on the profile page.
 */
public record TeamSummary(
    UUID teamId,
    String name,
    String domain,
    int developerCount,
    int completedSprints,
    LocalDateTime lastSprintAt
) {}

