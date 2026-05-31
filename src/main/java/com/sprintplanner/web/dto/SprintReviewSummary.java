package com.sprintplanner.web.dto;

import java.time.Instant;
import java.util.UUID;

public record SprintReviewSummary(
    UUID reviewId,
    String goalResult,
    String blockerNote,
    int totalPlannedPts,
    int totalCompletedPts,
    Instant submittedAt
) {}
