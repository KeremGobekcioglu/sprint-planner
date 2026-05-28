package com.sprintplanner.domain.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The single seam between all entry points and the system.
 */
public record PlanningRequest(
    UUID teamId,
    UUID userId,
    List<Epic> epics,
    String domain,           // E_COMMERCE | FINTECH | GENERIC
    String sprintGoal,
    Map<UUID, Integer> daysOff,  // developerId -> days off this sprint
    String previousSprintContext // optional one-time import; never persisted to memory files
) {}
