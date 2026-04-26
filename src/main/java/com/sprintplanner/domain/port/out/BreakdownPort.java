package com.sprintplanner.domain.port.out;

import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.model.UserStory;
import java.util.List;

/**
 * Breaks high-level epics into user stories and tasks.
 */
public interface BreakdownPort {

    List<UserStory> breakdown(
        PlanningRequest request,
        String teamProfileMemory,
        String sprintHistoryMemory
    );
}
