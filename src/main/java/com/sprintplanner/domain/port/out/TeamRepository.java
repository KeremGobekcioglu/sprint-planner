package com.sprintplanner.domain.port.out;

import com.sprintplanner.domain.model.Team;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for team metadata and configuration.
 */
public interface TeamRepository {

    Team save(Team team);

    Optional<Team> findById(UUID teamId);
}
