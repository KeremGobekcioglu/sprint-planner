package com.sprintplanner.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sprintplanner.domain.model.Developer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CapacityCalculatorTest {

    private final CapacityCalculator calculator = new CapacityCalculator();

    @Test
    void usesFreshStartFactorWhenNoHistoryExists() {
        UUID devId = UUID.randomUUID();
        Developer developer = new Developer(devId, UUID.randomUUID(), "Kerem", "backend", 20);

        Map<UUID, Integer> result = calculator.calculateCapacity(
            List.of(developer),
            10,
            Map.of(),
            Map.of()
        );

        assertEquals(16, result.get(devId));
    }

    @Test
    void appliesDaysOffRatioAndHistoricalCompletionRate() {
        UUID devId = UUID.randomUUID();
        Developer developer = new Developer(devId, UUID.randomUUID(), "Asli", "frontend", 20);

        Map<UUID, Integer> result = calculator.calculateCapacity(
            List.of(developer),
            10,
            Map.of(devId, 2),
            Map.of(devId, 0.90d)
        );

        assertEquals(14, result.get(devId));
    }

    @Test
    void returnsZeroWhenDeveloperHasNoAvailableDays() {
        UUID devId = UUID.randomUUID();
        Developer developer = new Developer(devId, UUID.randomUUID(), "Ece", "qa", 13);

        Map<UUID, Integer> result = calculator.calculateCapacity(
            List.of(developer),
            10,
            Map.of(devId, 10),
            Map.of(devId, 1.00d)
        );

        assertEquals(0, result.get(devId));
    }
}
