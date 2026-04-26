package com.sprintplanner.domain.port.out;

import com.sprintplanner.domain.model.Developer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for developer data.
 */
public interface DeveloperRepository {

    Developer save(Developer developer);

    Optional<Developer> findById(UUID developerId);

    List<Developer> findByTeamId(UUID teamId);
}
