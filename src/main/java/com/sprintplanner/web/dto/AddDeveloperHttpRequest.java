package com.sprintplanner.web.dto;

import java.util.List;

public record AddDeveloperHttpRequest(
    String name,
    String role,
    List<String> capabilities,
    int baseVelocity,
    String seniority
) {
}
