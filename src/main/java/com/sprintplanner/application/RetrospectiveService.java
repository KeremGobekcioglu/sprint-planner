package com.sprintplanner.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintplanner.domain.dto.RetrospectiveReport;
import com.sprintplanner.domain.model.DevCompletion;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.model.SprintReview;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.infrastructure.ai.GeminiClientProvider;
import com.sprintplanner.infrastructure.persistence.DevCompletionJpaRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class RetrospectiveService {

    private final SprintRepository sprintRepository;
    private final DevCompletionJpaRepository devCompletionRepository;
    private final DeveloperRepository developerRepository;
    private final GeminiClientProvider gemini;
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(RetrospectiveService.class);

    public RetrospectiveService(
            SprintRepository sprintRepository,
            DevCompletionJpaRepository devCompletionRepository,
            DeveloperRepository developerRepository,
            GeminiClientProvider gemini,
            ObjectMapper objectMapper) {
        this.sprintRepository = sprintRepository;
        this.devCompletionRepository = devCompletionRepository;
        this.developerRepository = developerRepository;
        this.gemini = gemini;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public RetrospectiveReport generateReport(UUID sprintId) {
        Sprint sprint = sprintRepository.findWithStoriesAndTasksById(sprintId)
            .orElseThrow(() -> new IllegalArgumentException("Sprint not found: " + sprintId));

        SprintReview review = sprint.getSprintReview();
        if (review == null) {
            throw new IllegalStateException("Sprint has not been reviewed yet");
        }

        // 1. Build developer stats
        List<DevCompletion> completions = devCompletionRepository.findByReviewId(review.getId());

        // Pre-load previous sprint completions once to avoid N+1 queries in computeTrend
        List<Sprint> teamCompletedSprints = sprintRepository.findByTeamId(sprint.getTeamId())
            .stream()
            .filter(s -> s.getStatus() == Sprint.Status.COMPLETED && s.getSprintReview() != null)
            .sorted(Comparator.comparing(Sprint::getPlannedAt))
            .toList();
        boolean hasEnoughHistory = teamCompletedSprints.size() >= 2;
        List<DevCompletion> previousSprintCompletions = loadPreviousSprintCompletions(teamCompletedSprints);

        List<RetrospectiveReport.DeveloperStat> devStats = completions.stream()
            .map(dc -> {
                String name = developerRepository.findById(dc.getDeveloperId())
                    .map(Developer::getName).orElse(dc.getDeveloperId().toString());
                double rate = dc.getPlannedPts() == 0 ? 0 :
                    (double) dc.getCompletedPts() / dc.getPlannedPts();
                String trend = computeTrend(dc.getDeveloperId(), rate, previousSprintCompletions, hasEnoughHistory);
                return new RetrospectiveReport.DeveloperStat(
                    name, dc.getPlannedPts(), dc.getCompletedPts(),
                    Math.round(rate * 100.0) / 100.0, trend
                );
            })
            .toList();

        // 2. Detect patterns from this sprint vs history
        List<String> patterns = detectPatterns(sprint, review, devStats);

        // 3. Generate AI analysis
        String aiAnalysis = generateAiAnalysis(sprint, review, devStats, patterns);

        // 4. Generate recommendations
        List<String> recommendations = generateRecommendations(devStats, patterns, review);

        double completionRate = review.getTotalPlannedPts() == 0 ? 0 :
            (double) review.getTotalCompletedPts() / review.getTotalPlannedPts();

        return new RetrospectiveReport(
            sprintId,
            sprint.getGoal(),
            review.getGoalResult().name(),
            review.getTotalPlannedPts(),
            review.getTotalCompletedPts(),
            Math.round(completionRate * 100.0) / 100.0,
            devStats,
            patterns,
            recommendations,
            aiAnalysis
        );
    }

    private List<DevCompletion> loadPreviousSprintCompletions(List<Sprint> completedSprints) {
        if (completedSprints.size() < 2) return List.of();
        Sprint prevSprint = completedSprints.get(completedSprints.size() - 2);
        return devCompletionRepository.findByReviewId(prevSprint.getSprintReview().getId());
    }

    private String computeTrend(UUID developerId, double currentRate,
            List<DevCompletion> previousCompletions, boolean hasEnoughHistory) {
        if (!hasEnoughHistory) return "insufficient data";
        Double prevRate = previousCompletions.stream()
            .filter(dc -> dc.getDeveloperId().equals(developerId))
            .map(dc -> dc.getPlannedPts() == 0 ? 0.0 : (double) dc.getCompletedPts() / dc.getPlannedPts())
            .findFirst()
            .orElse(null);
        if (prevRate == null) return "new to team";
        if (currentRate > prevRate + 0.10) return "improving";
        if (currentRate < prevRate - 0.10) return "declining";
        return "stable";
    }

    private List<String> detectPatterns(Sprint sprint, SprintReview review,
            List<RetrospectiveReport.DeveloperStat> devStats) {
        List<String> patterns = new ArrayList<>();

        double rate = review.getTotalPlannedPts() == 0 ? 0 :
            (double) review.getTotalCompletedPts() / review.getTotalPlannedPts();
        if (rate < 0.70) {
            patterns.add(String.format(
                "Only %.0f%% of committed work was completed — significant overcommitment detected",
                rate * 100));
        }

        devStats.stream()
            .filter(d -> d.completionRate() < 0.60 && d.plannedPts() > 0)
            .forEach(d -> patterns.add(String.format(
                "%s completed only %.0f%% of their tasks — may be overloaded or blocked",
                d.developerName(), d.completionRate() * 100)));

        if ("NO".equals(review.getGoalResult().name())) {
            patterns.add("Sprint goal was not met — review whether goal was realistic or if blockers were underestimated");
        } else if ("PARTIALLY".equals(review.getGoalResult().name())) {
            patterns.add("Sprint goal was partially met — common when scope is ambitious or dependencies are external");
        }

        if (review.getBlockerNote() != null && !review.getBlockerNote().isBlank()
                && !"None".equalsIgnoreCase(review.getBlockerNote())) {
            patterns.add("Blocker reported: \"" + review.getBlockerNote() + "\"");
        }

        devStats.stream()
            .filter(d -> "declining".equals(d.trend()))
            .forEach(d -> patterns.add(d.developerName() + " is trending down — consider adjusting their workload next sprint"));

        return patterns;
    }

    private List<String> generateRecommendations(
            List<RetrospectiveReport.DeveloperStat> devStats,
            List<String> patterns, SprintReview review) {
        List<String> recs = new ArrayList<>();

        double rate = review.getTotalPlannedPts() == 0 ? 0 :
            (double) review.getTotalCompletedPts() / review.getTotalPlannedPts();

        if (rate < 0.75) {
            int suggestedReduction = (int) Math.round(review.getTotalPlannedPts() * (1 - rate));
            recs.add(String.format(
                "Reduce next sprint commitment by ~%d points to match team velocity", suggestedReduction));
        }

        devStats.stream()
            .filter(d -> "improving".equals(d.trend()))
            .forEach(d -> recs.add(
                d.developerName() + " is improving — consider giving them more challenging tasks next sprint"));

        devStats.stream()
            .filter(d -> d.completionRate() >= 0.95 && d.plannedPts() > 0)
            .forEach(d -> recs.add(
                d.developerName() + " completed nearly all assigned work — they may have capacity for more"));

        if (patterns.stream().anyMatch(p -> p.contains("Blocker"))) {
            recs.add("Address reported blocker before next sprint planning to prevent recurrence");
        }

        return recs;
    }

    private String generateAiAnalysis(Sprint sprint, SprintReview review,
            List<RetrospectiveReport.DeveloperStat> devStats, List<String> patterns) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an agile coach analyzing a sprint retrospective. ");
        prompt.append("Write a concise 2-3 paragraph analysis of this sprint.\n\n");
        prompt.append("Sprint goal: ").append(sprint.getGoal()).append("\n");
        prompt.append("Goal result: ").append(review.getGoalResult()).append("\n");
        prompt.append("Planned: ").append(review.getTotalPlannedPts()).append(" pts\n");
        prompt.append("Completed: ").append(review.getTotalCompletedPts()).append(" pts\n");

        if (review.getBlockerNote() != null && !review.getBlockerNote().isBlank()) {
            prompt.append("Blocker: ").append(review.getBlockerNote()).append("\n");
        }

        prompt.append("\nDeveloper performance:\n");
        for (RetrospectiveReport.DeveloperStat stat : devStats) {
            prompt.append(String.format("- %s: %d/%d pts (%.0f%%, %s)\n",
                stat.developerName(), stat.completedPts(), stat.plannedPts(),
                stat.completionRate() * 100, stat.trend()));
        }

        prompt.append("\nDetected patterns:\n");
        for (String pattern : patterns) {
            prompt.append("- ").append(pattern).append("\n");
        }

        prompt.append("\nWrite a constructive, honest retrospective analysis. ");
        prompt.append("Focus on what the data tells us, not generic agile advice. ");
        prompt.append("Be specific about this team's performance. Keep it under 200 words.");
        prompt.append("\nFormat your response as plain text paragraphs separated by a blank line (two newlines between paragraphs). No markdown, no bullet points, no headers.");

        try {
            return gemini.generate(prompt.toString(), 0.4f, GeminiClientProvider.MODEL_RETROSPECTIVE);
        } catch (Exception e) {
            logger.warn("Failed to generate AI analysis for retrospective: {}", e.getMessage());
            return "AI analysis unavailable. See patterns and recommendations above.";
        }
    }
}
