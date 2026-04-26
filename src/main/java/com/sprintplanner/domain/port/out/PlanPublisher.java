package com.sprintplanner.domain.port.out;

import com.sprintplanner.domain.dto.SprintPlan;
import java.util.UUID;

/**
 * Publishes approved sprint plans to an external system.
 */
public interface PlanPublisher {

    void publish(UUID teamId, SprintPlan sprintPlan);
}
