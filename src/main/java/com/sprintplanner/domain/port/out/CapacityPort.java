package com.sprintplanner.domain.port.out;

import com.sprintplanner.domain.model.Developer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calculates available sprint capacity by developer.
 */
public interface CapacityPort {

    Map<UUID, Integer> calculateCapacity(
        List<Developer> developers,
        int sprintLengthDays,
        Map<UUID, Integer> daysOffByDeveloper,
        String sprintHistoryMemory
    );
}
