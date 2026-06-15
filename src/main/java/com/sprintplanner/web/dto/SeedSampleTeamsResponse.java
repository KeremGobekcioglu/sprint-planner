package com.sprintplanner.web.dto;

public record SeedSampleTeamsResponse(
    int teamsCreated,
    int developersCreated,
    String firstTeamId
) {
}
