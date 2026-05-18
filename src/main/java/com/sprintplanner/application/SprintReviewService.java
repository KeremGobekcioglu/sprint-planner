package com.sprintplanner.application;

import com.sprintplanner.domain.model.DevCompletion;
import com.sprintplanner.domain.model.EditLog;
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

/**
 * Service responsible for handling sprint review submission (closing a sprint).
 */
@Service
public class SprintReviewService {

    private final SprintRepository sprintRepository;
    private final SprintReviewJpaRepository sprintReviewJpaRepository;
    private final DevCompletionJpaRepository devCompletionJpaRepository;

    public SprintReviewService(
        SprintRepository sprintRepository,
        SprintReviewJpaRepository sprintReviewJpaRepository,
        DevCompletionJpaRepository devCompletionJpaRepository
    ) {
        this.sprintRepository = sprintRepository;
        this.sprintReviewJpaRepository = sprintReviewJpaRepository;
        this.devCompletionJpaRepository = devCompletionJpaRepository;
    }

    /**
     * Submit a sprint review and perform the close flow:
     * - load sprint with stories/tasks
     * - mark tasks DONE / NOT_DONE based on taskCompletions map
     * - compute per-developer planned/completed points
     * - persist SprintReview and DevCompletion rows
     * - mark sprint COMPLETED and save
     * NOTE: This method intentionally does NOT trigger ReferenceBuilder/PreferenceService.
     */
    public void submitReview(SprintReviewRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.sprintId() == null) throw new IllegalArgumentException("sprintId is required");

        UUID sprintId = request.sprintId();

        Sprint sprint = sprintRepository.findWithStoriesAndTasksById(sprintId)
            .orElseThrow(() -> new IllegalArgumentException("Sprint not found: " + sprintId));

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
            // also attach to review in-memory
            review.setDevCompletions(devCompletions);
            sprintReviewJpaRepository.save(review);
        }

        // Mark sprint completed and attach review
        sprint.setStatus(Sprint.Status.COMPLETED);
        sprint.setSprintReview(review);
        sprintRepository.save(sprint);
    }
}
