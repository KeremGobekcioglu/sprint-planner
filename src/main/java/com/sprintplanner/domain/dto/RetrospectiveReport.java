package com.sprintplanner.domain.dto;

import java.util.List;
import java.util.UUID;

public record RetrospectiveReport(
    UUID sprintId,
    String sprintGoal,
    String goalResult,
    int plannedPoints,
    int completedPoints,
    double completionRate,
    List<DeveloperStat> developerStats,
    List<String> patterns,
    List<String> recommendations,
    String aiAnalysis
) {
    public record DeveloperStat(
        String developerName,
        int plannedPts,
        int completedPts,
        double completionRate,
        String trend
    ) {}
}
