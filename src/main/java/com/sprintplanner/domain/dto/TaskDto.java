package com.sprintplanner.domain.dto;

import java.util.UUID;

/**
 * Lightweight DTO for task serialization, breaking circular references.
 */
public record TaskDto(
    UUID id,
    UUID storyId,
    UUID assignedDevId,
    String title,
    String description,
    String acceptanceCriterion,
    String roleTag,
    int storyPoints,
    String status,
    String trelloCardId,
    String assignmentReason,
    boolean deferred
) {}
