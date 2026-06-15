package com.sprintplanner.web.dto;

import com.sprintplanner.application.TeamSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProfileResponse(
    UUID userId,
    String email,
    String name,
    String role,
    Instant createdAt,
    List<TeamSummary> teams
) {}
