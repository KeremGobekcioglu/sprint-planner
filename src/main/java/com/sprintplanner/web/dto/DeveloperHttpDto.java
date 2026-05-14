package com.sprintplanner.web.dto;

import com.sprintplanner.domain.model.Developer;
import java.util.List;
import java.util.UUID;

public record DeveloperHttpDto(
    UUID id,
    UUID teamId,
    String name,
    String role,
    List<String> capabilities,
    int baseVelocity
) {

    public static DeveloperHttpDto from(Developer developer) {
        return new DeveloperHttpDto(
            developer.getId(),
            developer.getTeamId(),
            developer.getName(),
            developer.getRole(),
            developer.getCapabilities(),
            developer.getBaseVelocity()
        );
    }
}
