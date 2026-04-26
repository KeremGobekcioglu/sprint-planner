package com.sprintplanner.domain.port.out;

import com.sprintplanner.domain.model.Sprint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for sprint aggregate state.
 */
public interface SprintRepository {

    Sprint save(Sprint sprint);

    Optional<Sprint> findById(UUID sprintId);

    List<Sprint> findByTeamId(UUID teamId);
}
