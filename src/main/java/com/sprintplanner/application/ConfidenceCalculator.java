package com.sprintplanner.application;

import com.sprintplanner.domain.dto.SprintConfidenceForecast;
import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.model.SprintReview;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.infrastructure.persistence.DevCompletionJpaRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConfidenceCalculator {

    private final SprintRepository sprintRepository;
    private final DevCompletionJpaRepository devCompletionRepository;

    public ConfidenceCalculator(SprintRepository sprintRepository,
            DevCompletionJpaRepository devCompletionRepository) {
        this.sprintRepository = sprintRepository;
        this.devCompletionRepository = devCompletionRepository;
    }

    public SprintConfidenceForecast calculate(UUID teamId, int commitmentPoints) {
        // Load completed sprints with reviews eagerly to avoid lazy-loading issues
        List<Sprint> completedSprints = sprintRepository.findByTeamIdWithReviews(teamId)
            .stream()
            .filter(s -> s.getStatus() == Sprint.Status.COMPLETED)
            .sorted(Comparator.comparing(Sprint::getPlannedAt))
            .toList();

        if (completedSprints.isEmpty()) {
            int realistic = (int) Math.round(commitmentPoints * 0.75);
            int defer = Math.max(0, commitmentPoints - realistic);
            return new SprintConfidenceForecast(
                commitmentPoints, realistic, defer,
                0.75, 0.75, 0,
                "No sprint history yet. Using conservative 75% completion estimate for fresh teams.",
                List.of("This is your first sprint — the AI will learn from your team's actual performance after you close it.")
            );
        }

        // For each completed sprint, calculate completion rate from SprintReview
        List<Double> completionRates = completedSprints.stream()
            .map(Sprint::getSprintReview)
            .filter(review -> review != null && review.getTotalPlannedPts() > 0)
            .map(review -> (double) review.getTotalCompletedPts() / review.getTotalPlannedPts())
            .toList();

        if (completionRates.isEmpty()) {
            int realistic = (int) Math.round(commitmentPoints * 0.75);
            int defer = Math.max(0, commitmentPoints - realistic);
            return new SprintConfidenceForecast(
                commitmentPoints, realistic, defer, 0.75, 0.75, 0,
                "Sprint history exists but no review data found. Using 75% estimate.",
                List.of()
            );
        }

        // Weighted average — recent sprints weighted more
        double historicalRate = calculateWeightedAverage(completionRates);
        int sprintsAnalyzed = completionRates.size();
        int realistic = (int) Math.round(commitmentPoints * historicalRate);
        int defer = Math.max(0, commitmentPoints - realistic);

        // How often has team met or exceeded this rate?
        long timesMetOrExceeded = completedSprints.stream()
            .map(Sprint::getSprintReview)
            .filter(r -> r != null && r.getTotalPlannedPts() > 0)
            .filter(r -> (double) r.getTotalCompletedPts() / r.getTotalPlannedPts() >= historicalRate)
            .count();
        double probability = (double) timesMetOrExceeded / sprintsAnalyzed;

        List<String> insights = generateInsights(completedSprints, commitmentPoints, realistic, historicalRate);
        String summary = buildSummary(commitmentPoints, realistic, defer, historicalRate, sprintsAnalyzed);

        return new SprintConfidenceForecast(
            commitmentPoints, realistic, defer,
            probability, historicalRate, sprintsAnalyzed,
            summary, insights
        );
    }

    private double calculateWeightedAverage(List<Double> rates) {
        double totalWeight = 0;
        double weightedSum = 0;
        for (int i = 0; i < rates.size(); i++) {
            double weight = i + 1;
            weightedSum += rates.get(i) * weight;
            totalWeight += weight;
        }
        return totalWeight == 0 ? 0.75 : weightedSum / totalWeight;
    }

    private List<String> generateInsights(List<Sprint> sprints, int commitment, int realistic, double rate) {
        List<String> insights = new ArrayList<>();

        if (rate < 0.70) {
            insights.add("Your team consistently completes less than 70% of committed work. Consider reducing scope significantly.");
        } else if (rate < 0.85) {
            insights.add(String.format("Your team completes %.0f%% of committed work on average. Some buffer is recommended.", rate * 100));
        } else {
            insights.add(String.format("Your team has a strong completion rate of %.0f%%. This plan is realistic.", rate * 100));
        }

        if (sprints.size() >= 2) {
            Sprint lastSprint = sprints.get(sprints.size() - 1);
            SprintReview lastReview = lastSprint.getSprintReview();
            if (lastReview != null && lastReview.getTotalPlannedPts() > 0) {
                double lastRate = (double) lastReview.getTotalCompletedPts() / lastReview.getTotalPlannedPts();
                if (lastRate > rate + 0.10) {
                    insights.add("Trending up — your last sprint performed better than average.");
                } else if (lastRate < rate - 0.10) {
                    insights.add("Trending down — your last sprint performed worse than average. Consider reducing commitment.");
                }
            }
        }

        if (realistic < commitment) {
            insights.add(String.format(
                "Deferring %d points would align this sprint with your team's realistic capacity.",
                commitment - realistic
            ));
        }

        return insights;
    }

    private String buildSummary(int commitment, int realistic, int defer, double rate, int sprints) {
        if (defer == 0) {
            return String.format(
                "Based on %d sprint%s, your team completes %.0f%% of committed work. This plan looks achievable.",
                sprints, sprints == 1 ? "" : "s", rate * 100
            );
        }
        return String.format(
            "Based on %d sprint%s, your team completes %.0f%% of committed work. " +
            "Realistically, expect to complete %d of %d committed points. " +
            "Consider deferring %d points to reduce overcommitment risk.",
            sprints, sprints == 1 ? "" : "s", rate * 100,
            realistic, commitment, defer
        );
    }
}
