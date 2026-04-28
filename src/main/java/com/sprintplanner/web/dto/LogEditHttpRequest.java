package com.sprintplanner.web.dto;

import java.util.UUID;

public record LogEditHttpRequest(
    String field,
    String entityId,
    String oldValue,
    String newValue,
    String taskType
) {
}
