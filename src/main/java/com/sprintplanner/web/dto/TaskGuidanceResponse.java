package com.sprintplanner.web.dto;

import java.util.List;

public record TaskGuidanceResponse(
    String hints,
    String suggestedApproach,
    List<String> riskFlags
) {}
