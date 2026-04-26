package com.sprintplanner.web.dto;

import com.sprintplanner.domain.model.Team;
import java.time.Instant;
import java.util.UUID;

public record TeamHttpDto(
    UUID id,
    String name,
    String domain,
    int sprintLengthDays,
    String trelloBoardId,
    Instant createdAt
) {

    public static TeamHttpDto from(Team team) {
        return new TeamHttpDto(
            team.getId(),
            team.getName(),
            team.getDomain(),
            team.getSprintLengthDays(),
            team.getTrelloBoardId(),
            team.getCreatedAt()
        );
    }
}
