package com.sprintplanner.domain.port.out;

public interface AdminResetPort {

    /**
     * Deletes all sprint-related data (edit_log, dev_completion, sprint_review,
     * task, user_story, sprint) in FK-safe order. Teams and developers are not touched.
     */
    void resetAllSprintData();
}
