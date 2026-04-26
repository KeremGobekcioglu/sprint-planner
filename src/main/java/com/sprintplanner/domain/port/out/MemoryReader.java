package com.sprintplanner.domain.port.out;

import java.util.UUID;

/**
 * Reads team memory files used in planning prompts.
 */
public interface MemoryReader {

    String readTeamProfile(UUID teamId);

    String readSprintHistory(UUID teamId);
}
