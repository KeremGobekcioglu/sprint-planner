package com.sprintplanner.domain.port.out;

import java.util.UUID;

/**
 * Writes team memory files after reviews and edits.
 */
public interface MemoryWriter {

    void writeTeamProfile(UUID teamId, String content);

    void writeSprintHistory(UUID teamId, String content);
}
