package com.sprintplanner.web.dto;

import java.util.List;
import java.util.UUID;

public record SubmitReviewHttpRequest(
    UUID teamId,
    ReviewHttpDto latestReview,
    List<ReviewHttpDto> rollingWindowReviews,
    List<EditLogHttpDto> editLogs
) {
}
