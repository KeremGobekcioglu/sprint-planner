package com.sprintplanner.web.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Request payload for submitting a sprint review from the frontend.
 * Accept taskCompletions as Map<String,Boolean> where key is taskId string.
 */
public record SprintReviewRequest(
    UUID teamId,
    UUID sprintId,
    String goalResult,
    String blockerNote,
    Map<String, Boolean> taskCompletions,
    Integer totalPlannedPts,
    Integer totalCompletedPts
) {}

