package com.sprintplanner.web.dto;
public record ProfileStatsResponse(
    int totalSprintsPlanned,
    int totalSprintsCompleted,
    double averageVelocityAccuracy,
    int goalHitRate,
    String mostActiveTeam
) {}