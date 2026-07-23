package com.sprintplanner.web.dto;

import com.sprintplanner.domain.dto.TaskDto;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PlanComparisonResponse(
    List<TaskDto> aiPlan,
    List<TaskDto> finalPlan,
    int editCount,
    int pointsDelta,
    Set<UUID> changedTaskIds
) {}
