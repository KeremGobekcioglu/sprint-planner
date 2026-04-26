package com.sprintplanner.web.dto;

import java.time.Instant;
import java.util.UUID;

public record EditLogHttpDto(
    UUID id,
    UUID sprintId,
    String field,
    String originalValue,
    String newValue,
    String taskType,
    Instant loggedAt
) {
}
