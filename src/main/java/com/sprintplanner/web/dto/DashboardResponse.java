package com.sprintplanner.web.dto;

import com.sprintplanner.application.TeamSummary;
import java.util.List;

public record DashboardResponse(
    List<ActiveSprintSummary> activeSprints,
    List<TeamSummary> teams,
    List<RecentSprintSummary> recentSprints
) {}
