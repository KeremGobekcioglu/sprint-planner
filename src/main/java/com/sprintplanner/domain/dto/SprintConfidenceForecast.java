package com.sprintplanner.domain.dto;

import java.util.List;

public record SprintConfidenceForecast(
    int commitmentPoints,
    int realisticCompletionPoints,
    int recommendedDeferPoints,
    double completionProbability,
    double historicalCompletionRate,
    int sprintsAnalyzed,
    String forecastSummary,
    List<String> insights
) {}
