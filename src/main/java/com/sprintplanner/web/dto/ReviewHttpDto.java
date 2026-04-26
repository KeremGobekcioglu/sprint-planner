package com.sprintplanner.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewHttpDto(
    UUID id,
    UUID sprintId,
    String goalResult,
    String blockerNote,
    int totalPlannedPts,
    int totalCompletedPts,
    Instant submittedAt,
    List<DevCompletionHttpDto> devCompletions
) {
}
