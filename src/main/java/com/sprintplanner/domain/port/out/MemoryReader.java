package com.sprintplanner.domain.port.out;

import java.time.Instant;
import java.util.UUID;

public interface MemoryReader {

    String readTeamProfile(UUID teamId);

    String readSprintHistory(UUID teamId);

    Instant getTeamProfileLastUpdated(UUID teamId);

    Instant getSprintHistoryLastUpdated(UUID teamId);
}
