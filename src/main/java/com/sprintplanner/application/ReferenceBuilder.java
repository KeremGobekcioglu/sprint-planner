package com.sprintplanner.application;

import com.sprintplanner.domain.model.DevCompletion;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.SprintReview;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.MemoryWriter;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReferenceBuilder {

    private final MemoryWriter memoryWriter;
    private final DeveloperRepository developerRepository;

    public ReferenceBuilder(MemoryWriter memoryWriter, DeveloperRepository developerRepository) {
        this.memoryWriter = memoryWriter;
        this.developerRepository = developerRepository;
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
            builder.append("## Sprint Review ").append(review.getId()).append(System.lineSeparator())
                .append("- Goal result: ").append(review.getGoalResult()).append(System.lineSeparator())
                .append("- Planned points: ").append(review.getTotalPlannedPts()).append(System.lineSeparator())
                .append("- Completed points: ").append(review.getTotalCompletedPts()).append(System.lineSeparator())
                .append("- Blocker: ").append(review.getBlockerNote() == null ? "None" : review.getBlockerNote())
                .append(System.lineSeparator());

            if (review.getDevCompletions() != null && !review.getDevCompletions().isEmpty()) {
                builder.append("- Developer completion:").append(System.lineSeparator());
                for (DevCompletion completion : review.getDevCompletions()) {
                    String displayName = developerRepository.findById(completion.getDeveloperId())
                        .map(Developer::getName)
                        .orElse(completion.getDeveloperId().toString());

                    builder.append("  - ")
                        .append(displayName)
                        .append(" planned ")
                        .append(completion.getPlannedPts())
                        .append(", completed ")
                        .append(completion.getCompletedPts())
                        .append(System.lineSeparator());
                }
            }
            builder.append(System.lineSeparator());
        }

        return builder.toString();
    }
}
