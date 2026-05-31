package com.sprintplanner.application;

import com.sprintplanner.domain.model.DevCompletion;
import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.model.SprintReview;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.infrastructure.persistence.DevCompletionJpaRepository;
import com.sprintplanner.infrastructure.persistence.SprintReviewJpaRepository;
import com.sprintplanner.web.dto.SprintReviewRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SprintReviewService {

    private final SprintRepository sprintRepository;
    private final SprintReviewJpaRepository sprintReviewJpaRepository;
    private final DevCompletionJpaRepository devCompletionJpaRepository;
    private final ReferenceBuilder referenceBuilder;

    public SprintReviewService(
        SprintRepository sprintRepository,
        SprintReviewJpaRepository sprintReviewJpaRepository,
        DevCompletionJpaRepository devCompletionJpaRepository,
        ReferenceBuilder referenceBuilder
    ) {
        this.sprintRepository = sprintRepository;
        this.sprintReviewJpaRepository = sprintReviewJpaRepository;
        this.devCompletionJpaRepository = devCompletionJpaRepository;
        this.referenceBuilder = referenceBuilder;
    }

    /**
     * Submit a sprint review and perform the close flow:
     * - load sprint with stories/tasks
     * - mark tasks DONE / NOT_DONE based on taskCompletions map
     * - compute per-developer planned/completed points
     * - persist SprintReview and DevCompletion rows
     * - mark sprint COMPLETED and save
     * - trigger ReferenceBuilder to update sprint_history.md
     */
    @Transactional
    public void submitReview(SprintReviewRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.sprintId() == null) throw new IllegalArgumentException("sprintId is required");

        UUID sprintId = request.sprintId();

        Sprint sprint = sprintRepository.findWithStoriesAndTasksById(sprintId)
            .orElseThrow(() -> new IllegalArgumentException("Sprint not found: " + sprintId));

        boolean reviewExists = sprintReviewJpaRepository.existsBySprintId(sprintId);
        if (reviewExists) {
            throw new IllegalStateException("Sprint " + sprintId + " has already been reviewed.");
        }

        Map<String, Boolean> taskCompletions = request.taskCompletions() == null ? Map.of() : request.taskCompletions();

        int totalPlanned = 0;
        int totalCompleted = 0;

        // Map developerId -> int[planned, completed]
        Map<UUID, int[]> perDev = new HashMap<>();

        // Iterate stories and tasks
        if (sprint.getUserStories() != null) {
            for (var story : sprint.getUserStories()) {
                if (story.getTasks() == null) continue;
                for (var task : story.getTasks()) {
                    int pts = task.getStoryPoints();
                    totalPlanned += pts;

                    String taskKey = task.getId() == null ? null : task.getId().toString();
                    boolean completed = false;
                    if (taskKey != null) {
                        completed = taskCompletions.getOrDefault(taskKey, false);
                    }

                    if (completed) {
                        task.setStatus("DONE");
                        totalCompleted += pts;
                    } else {
                        task.setStatus("NOT_DONE");
                    }

                    UUID devId = task.getAssignedDevId();
                    if (devId != null) {
                        int[] arr = perDev.getOrDefault(devId, new int[] {0,0});
                        arr[0] += pts; // planned
                        if (completed) arr[1] += pts; // completed
                        perDev.put(devId, arr);
                    }
                }
            }
        }

        // Persist task status changes by saving sprint aggregate
        sprintRepository.save(sprint);

        // Build SprintReview entity
        UUID reviewId = UUID.randomUUID();
        SprintReview.GoalResult goal = SprintReview.GoalResult.valueOf(request.goalResult() == null ? "PARTIALLY" : request.goalResult());
        SprintReview review = new SprintReview(
            reviewId,
            sprintId,
            goal,
            request.blockerNote(),
            request.totalPlannedPts() == null ? totalPlanned : request.totalPlannedPts(),
            request.totalCompletedPts() == null ? totalCompleted : request.totalCompletedPts(),
            Instant.now()
        );

        // Persist review
        sprintReviewJpaRepository.save(review);

        // Persist DevCompletion rows
        List<DevCompletion> devCompletions = new ArrayList<>();
        for (Map.Entry<UUID, int[]> e : perDev.entrySet()) {
            UUID developerId = e.getKey();
            int planned = e.getValue()[0];
            int completed = e.getValue()[1];
            DevCompletion dc = new DevCompletion(UUID.randomUUID(), review.getId(), developerId, planned, completed);
            devCompletions.add(dc);
        }

        if (!devCompletions.isEmpty()) {
            devCompletionJpaRepository.saveAll(devCompletions);
        }

        // Mark sprint completed
        sprint.setStatus(Sprint.Status.COMPLETED);
        sprintRepository.save(sprint);

        // Update sprint history memory for this team
        UUID teamId = sprint.getTeamId();
        List<SprintReview> teamReviews = sprintRepository.findByTeamIdWithReviews(teamId).stream()
            .map(Sprint::getSprintReview)
            .filter(r -> r != null)
            .toList();
        referenceBuilder.updateSprintHistory(teamId, teamReviews);
    }
}
