package com.sprintplanner.domain.dto;

import java.util.List;
import java.util.UUID;

/**
 * Lightweight DTO for user story serialization, breaking circular references.
 */
public record UserStoryDto(
    UUID id,
    UUID sprintId,
    String epicTitle,
    String title,
    int storyPoints,
    String status,
    List<TaskDto> tasks
) {}

