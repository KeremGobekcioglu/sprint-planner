package com.sprintplanner.infrastructure.capacity;

import com.sprintplanner.domain.model.DevCompletion;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.model.SprintReview;
import com.sprintplanner.domain.port.out.CapacityPort;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.domain.service.CapacityCalculator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CapacityCalculatorAdapter implements CapacityPort {

    private final CapacityCalculator capacityCalculator;
    private final SprintRepository sprintRepository;

    public CapacityCalculatorAdapter(SprintRepository sprintRepository) {
        this.capacityCalculator = new CapacityCalculator();
        this.sprintRepository = sprintRepository;
    }

    @Override
    public Map<UUID, Integer> calculateCapacity(
        List<Developer> developers,
        int sprintLengthDays,
        Map<UUID, Integer> daysOffByDeveloper,
        String sprintHistoryMemory,
        UUID teamId
    ) {
        Map<UUID, Double> completionRates = teamId != null ? computeCompletionRates(teamId) : Map.of();
        return capacityCalculator.calculateCapacity(
            developers,
            sprintLengthDays,
            daysOffByDeveloper,
            completionRates
        );
    }

    private Map<UUID, Double> computeCompletionRates(UUID teamId) {
        List<Sprint> sprints = sprintRepository.findByTeamIdWithReviews(teamId);

        // devId -> [totalPlanned, totalCompleted]
        Map<UUID, long[]> devTotals = new HashMap<>();

        for (Sprint sprint : sprints) {
            if (sprint.getStatus() != Sprint.Status.COMPLETED) continue;
            SprintReview review = sprint.getSprintReview();
            if (review == null || review.getDevCompletions() == null) continue;

            for (DevCompletion dc : review.getDevCompletions()) {
                long[] totals = devTotals.computeIfAbsent(dc.getDeveloperId(), k -> new long[]{0, 0});
                totals[0] += dc.getPlannedPts();
                totals[1] += dc.getCompletedPts();
            }
        }

        Map<UUID, Double> rates = new HashMap<>();
        for (Map.Entry<UUID, long[]> entry : devTotals.entrySet()) {
            long planned = entry.getValue()[0];
            long completed = entry.getValue()[1];
            if (planned > 0) {
                rates.put(entry.getKey(), Math.min(1.0, (double) completed / planned));
            }
        }
        return rates;
    }
}
