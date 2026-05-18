package com.sprintplanner.application;

import com.sprintplanner.domain.dto.Epic;
import java.util.List;

public record SamplePlanningDefaults(
    String sprintGoal,
    List<Epic> epics
) {
}
