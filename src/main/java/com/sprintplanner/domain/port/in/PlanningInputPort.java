package com.sprintplanner.domain.port.in;

import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.dto.SprintPlan;

/**
 * Primary use-case entry point for generating sprint plans.
 */
public interface PlanningInputPort {

    SprintPlan planSprint(PlanningRequest request);
}
