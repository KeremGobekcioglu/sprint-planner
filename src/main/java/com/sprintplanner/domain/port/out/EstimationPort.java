package com.sprintplanner.domain.port.out;

import com.sprintplanner.domain.model.UserStory;
import java.util.List;

/**
 * Estimates story points for generated tasks and stories.
 */
public interface EstimationPort {

    List<UserStory> estimate(List<UserStory> userStories, String sprintHistoryMemory);
}
