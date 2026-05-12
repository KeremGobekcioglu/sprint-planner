package com.sprintplanner.web.dto;
import java.time.Instant;
public record TeamMemoryResponse(
    String teamProfile,
    String sprintHistory,
    Instant teamProfileLastUpdated,
    Instant sprintHistoryLastUpdated
) {}