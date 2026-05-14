package com.sprintplanner.domain.port.out;

import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.model.UserStory;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assigns estimated tasks to developers within capacity constraints.
 */
public interface DistributionPort {

    SprintPlan distribute(
        UUID teamId,
        String sprintGoal,
        List<UserStory> estimatedUserStories,
        Map<UUID, Integer> capacityByDeveloper,
        Map<UUID, List<String>> developerCapabilities,
        String teamProfileMemory,
        String sprintHistoryMemory
    );
}
