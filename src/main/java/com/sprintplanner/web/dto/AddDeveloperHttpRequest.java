package com.sprintplanner.web.dto;

public record AddDeveloperHttpRequest(
    String name,
    String role,
    int baseVelocity
) {
}
