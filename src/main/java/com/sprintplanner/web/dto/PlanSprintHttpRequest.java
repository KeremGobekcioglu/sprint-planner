package com.sprintplanner.web.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlanSprintHttpRequest(
    UUID teamId,
    List<EpicHttpDto> epics,
    String domain,
    String sprintGoal,
    Map<UUID, Integer> daysOff,
    String previousSprintContext
) {
}
