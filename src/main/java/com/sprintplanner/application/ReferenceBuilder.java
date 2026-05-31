package com.sprintplanner.application;

import com.sprintplanner.domain.model.DevCompletion;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.model.SprintReview;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.MemoryWriter;
import com.sprintplanner.domain.port.out.SprintRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReferenceBuilder {

    private final MemoryWriter memoryWriter;
    private final DeveloperRepository developerRepository;
    private final SprintRepository sprintRepository;

    public ReferenceBuilder(MemoryWriter memoryWriter, DeveloperRepository developerRepository,
            SprintRepository sprintRepository) {
        this.memoryWriter = memoryWriter;
        this.developerRepository = developerRepository;
        this.sprintRepository = sprintRepository;
    }

    public void updateSprintHistory(UUID teamId, List<SprintReview> reviews) {
        String markdown = buildSprintHistoryMarkdown(reviews);
        memoryWriter.writeSprintHistory(teamId, markdown);
    }

    public String buildSprintHistoryMarkdown(List<SprintReview> reviews) {
        List<SprintReview> sorted = (reviews == null ? List.<SprintReview>of() : reviews).stream()
            .sorted(Comparator.comparing(SprintReview::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(3)
            .toList();

        StringBuilder builder = new StringBuilder();
        builder.append("# Sprint History").append(System.lineSeparator())
            .append(System.lineSeparator())
            .append("Generated at: ").append(Instant.now()).append(System.lineSeparator())
            .append(System.lineSeparator());

        if (sorted.isEmpty()) {
            builder.append("- No sprint review data available yet.").append(System.lineSeparator());
            return builder.toString();
        }

        for (SprintReview review : sorted) {
            Sprint sprint = sprintRepository.findById(review.getSprintId()).orElse(null);
            String goalText = sprint != null && sprint.getGoal() != null ? sprint.getGoal() : "Unknown";
            String sprintLabel = sprint != null
                ? "Sprint " + sprint.getSprintNumber() + " — " + goalText
                : "Sprint — " + goalText;

            builder.append("## ").append(sprintLabel).append(System.lineSeparator())
                .append("Goal result: ").append(review.getGoalResult()).append(System.lineSeparator())
                .append("Planned: ").append(review.getTotalPlannedPts())
                .append(" pts | Completed: ").append(review.getTotalCompletedPts()).append(" pts")
                .append(System.lineSeparator())
                .append("Blocker: ").append(review.getBlockerNote() == null ? "None" : review.getBlockerNote())
                .append(System.lineSeparator());

            if (review.getDevCompletions() != null && !review.getDevCompletions().isEmpty()) {
                builder.append(System.lineSeparator())
                    .append("Developer completion:").append(System.lineSeparator())
                    .append("| Developer | Planned | Completed | Rate |").append(System.lineSeparator())
                    .append("|-----------|---------|-----------|------|").append(System.lineSeparator());

                for (DevCompletion completion : review.getDevCompletions()) {
                    String displayName = developerRepository.findById(completion.getDeveloperId())
                        .map(Developer::getName)
                        .orElse(completion.getDeveloperId().toString());

                    int planned = completion.getPlannedPts();
                    int completed = completion.getCompletedPts();
                    String rate = planned > 0
                        ? Math.round((double) completed / planned * 100) + "%"
                        : "N/A";

                    builder.append("| ").append(displayName)
                        .append(" | ").append(planned)
                        .append(" | ").append(completed)
                        .append(" | ").append(rate)
                        .append(" |").append(System.lineSeparator());
                }
            }

            builder.append(System.lineSeparator());
        }

        return builder.toString();
    }
}
