package com.sprintplanner.web.dto;

public record CreateTeamHttpRequest(
    String name,
    String domain,
    int sprintLengthDays,
    String trelloBoardId
) {
}
