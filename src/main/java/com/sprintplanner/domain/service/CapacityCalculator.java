package com.sprintplanner.domain.service;

import com.sprintplanner.domain.model.Developer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure domain algorithm for per-developer sprint capacity.
 */
public class CapacityCalculator {

    private static final double FRESH_START_FACTOR = 0.80d;

    public Map<UUID, Integer> calculateCapacity(
        List<Developer> developers,
        int sprintLengthDays,
        Map<UUID, Integer> daysOffByDeveloper,
        Map<UUID, Double> completionRateByDeveloper
    ) {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        if (developers == null || developers.isEmpty() || sprintLengthDays <= 0) {
            return result;
        }

        for (Developer developer : developers) {
            if (developer == null || developer.getId() == null) {
                continue;
            }

            int daysOff = 0;
            if (daysOffByDeveloper != null) {
                daysOff = Math.max(0, daysOffByDeveloper.getOrDefault(developer.getId(), 0));
            }

            int availableDays = Math.max(0, sprintLengthDays - daysOff);
            if (availableDays == 0) {
                result.put(developer.getId(), 0);
                continue;
            }

            double availabilityRatio = (double) availableDays / sprintLengthDays;
            double completionFactor = resolveCompletionFactor(developer.getId(), completionRateByDeveloper);
            double rawCapacity = developer.getBaseVelocity() * availabilityRatio * completionFactor;
            int roundedCapacity = Math.max(0, (int) Math.round(rawCapacity));
            result.put(developer.getId(), roundedCapacity);
        }

        return result;
    }

    private double resolveCompletionFactor(UUID developerId, Map<UUID, Double> completionRateByDeveloper) {
        if (completionRateByDeveloper == null || !completionRateByDeveloper.containsKey(developerId)) {
            return FRESH_START_FACTOR;
        }

        Double rate = completionRateByDeveloper.get(developerId);
        if (rate == null) {
            return FRESH_START_FACTOR;
        }

        return Math.max(0.0d, rate);
    }
}
