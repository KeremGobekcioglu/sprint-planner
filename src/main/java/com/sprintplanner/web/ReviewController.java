package com.sprintplanner.web;

import com.sprintplanner.application.SprintReviewService;
import com.sprintplanner.domain.model.DevCompletion;
import com.sprintplanner.domain.model.EditLog;
import com.sprintplanner.domain.model.SprintReview;
import com.sprintplanner.domain.port.out.EditLogRepository;
import com.sprintplanner.web.dto.DevCompletionHttpDto;
import com.sprintplanner.web.dto.EditLogHttpDto;
import com.sprintplanner.web.dto.ReviewHttpDto;
import com.sprintplanner.web.dto.SubmitReviewHttpRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final SprintReviewService sprintReviewService;
    private final EditLogRepository editLogRepository;

    public ReviewController(SprintReviewService sprintReviewService, EditLogRepository editLogRepository) {
        this.sprintReviewService = sprintReviewService;
        this.editLogRepository = editLogRepository;
    }

    // Accept POST /api/reviews to match frontend (was POST /api/reviews/process)
    @PostMapping
    public ResponseEntity<Void> submitReview(@RequestBody com.sprintplanner.web.dto.SprintReviewRequest request) {
        // Delegate to application service which performs the close/submit workflow
        sprintReviewService.submitReview(request);
        return ResponseEntity.ok().build();
    }

    private SprintReview toSprintReview(ReviewHttpDto dto) {
        SprintReview review = new SprintReview(
            defaultUuid(dto.id()),
            dto.sprintId(),
            SprintReview.GoalResult.valueOf(dto.goalResult()),
            dto.blockerNote(),
            dto.totalPlannedPts(),
            dto.totalCompletedPts(),
            dto.submittedAt()
        );

        List<DevCompletion> completions = dto.devCompletions() == null
            ? List.of()
            : dto.devCompletions().stream().map(this::toDevCompletion).toList();
        review.setDevCompletions(completions);
        return review;
    }

    private DevCompletion toDevCompletion(DevCompletionHttpDto dto) {
        return new DevCompletion(
            defaultUuid(dto.id()),
            dto.reviewId(),
            dto.developerId(),
            dto.plannedPts(),
            dto.completedPts()
        );
    }

    private EditLog toEditLog(EditLogHttpDto dto) {
        return new EditLog(
            defaultUuid(dto.id()),
            dto.sprintId(),
            dto.field(),
            dto.originalValue(),
            dto.newValue(),
            dto.taskType(),
            dto.loggedAt()
        );
    }

    private UUID defaultUuid(UUID value) {
        return value == null ? UUID.randomUUID() : value;
    }
}
