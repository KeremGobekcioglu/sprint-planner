package com.sprintplanner.infrastructure.capacity;

import com.sprintplanner.domain.model.DevCompletion;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.port.out.CapacityPort;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.domain.service.CapacityCalculator;
import com.sprintplanner.infrastructure.persistence.DevCompletionJpaRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CapacityCalculatorAdapter implements CapacityPort {

    private static final Logger logger = LoggerFactory.getLogger(CapacityCalculatorAdapter.class);

    private final CapacityCalculator capacityCalculator;
    private final SprintRepository sprintRepository;
    private final DevCompletionJpaRepository devCompletionRepository;

    public CapacityCalculatorAdapter(
        SprintRepository sprintRepository,
        DevCompletionJpaRepository devCompletionRepository
    ) {
        this.capacityCalculator = new CapacityCalculator();
        this.sprintRepository = sprintRepository;
        this.devCompletionRepository = devCompletionRepository;
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
        logger.info("[CAPACITY] Team {}: {} developers have historical completion rates",
            teamId, completionRates.size());
        return capacityCalculator.calculateCapacity(
            developers,
            sprintLengthDays,
            daysOffByDeveloper,
            completionRates
        );
    }

    private Map<UUID, Double> computeCompletionRates(UUID teamId) {
        List<Sprint> completedSprints = sprintRepository.findByTeamId(teamId)
            .stream()
            .filter(s -> s.getStatus() == Sprint.Status.COMPLETED)
            .toList();

        if (completedSprints.isEmpty()) return Map.of();

        Map<UUID, List<Double>> ratesByDev = new HashMap<>();

        for (Sprint sprint : completedSprints) {
            if (sprint.getSprintReview() == null) continue;
            UUID reviewId = sprint.getSprintReview().getId();
            List<DevCompletion> completions = devCompletionRepository.findByReviewId(reviewId);
            for (DevCompletion dc : completions) {
                if (dc.getPlannedPts() <= 0) continue;
                double rate = (double) dc.getCompletedPts() / dc.getPlannedPts();
                ratesByDev.computeIfAbsent(dc.getDeveloperId(), k -> new ArrayList<>()).add(rate);
            }
        }

        Map<UUID, Double> result = new HashMap<>();
        for (Map.Entry<UUID, List<Double>> entry : ratesByDev.entrySet()) {
            List<Double> rates = entry.getValue();
            // Weight recent sprints more — same approach as ConfidenceCalculator
            double totalWeight = 0;
            double weightedSum = 0;
            for (int i = 0; i < rates.size(); i++) {
                double weight = i + 1;
                weightedSum += rates.get(i) * weight;
                totalWeight += weight;
            }
            result.put(entry.getKey(), weightedSum / totalWeight);
        }
        return result;
    }
}
