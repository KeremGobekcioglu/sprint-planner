package com.sprintplanner.web.dto;

import java.util.UUID;

public record DevCompletionHttpDto(
    UUID id,
    UUID reviewId,
    UUID developerId,
    int plannedPts,
    int completedPts
) {
}
