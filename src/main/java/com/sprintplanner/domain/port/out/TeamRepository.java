package com.sprintplanner.domain.port.out;

import com.sprintplanner.domain.model.Team;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for team metadata and configuration.
 */
public interface TeamRepository {

    Team save(Team team);

    Optional<Team> findById(UUID teamId);

    List<Team> findAll();

    List<Team> findByOwnerId(UUID ownerId);
}
