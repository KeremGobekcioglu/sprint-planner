package com.sprintplanner.infrastructure.capacity;

import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.port.out.CapacityPort;
import com.sprintplanner.domain.service.CapacityCalculator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CapacityCalculatorAdapter implements CapacityPort {

    private final CapacityCalculator capacityCalculator;

    public CapacityCalculatorAdapter() {
        this.capacityCalculator = new CapacityCalculator();
    }

    @Override
    public Map<UUID, Integer> calculateCapacity(
        List<Developer> developers,
        int sprintLengthDays,
        Map<UUID, Integer> daysOffByDeveloper,
        String sprintHistoryMemory
    ) {
        // History parsing can be added later; for now the domain service applies
        // fresh-start factor when no completion rates are provided.
        return capacityCalculator.calculateCapacity(
            developers,
            sprintLengthDays,
            daysOffByDeveloper,
            Map.of()
        );
    }
}
