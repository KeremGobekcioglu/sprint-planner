package com.sprintplanner.application;

import com.sprintplanner.domain.model.EditLog;
import com.sprintplanner.domain.model.SprintReview;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SprintReviewService {

    private final PreferenceService preferenceService;
    private final ReferenceBuilder referenceBuilder;

    public SprintReviewService(PreferenceService preferenceService, ReferenceBuilder referenceBuilder) {
        this.preferenceService = preferenceService;
        this.referenceBuilder = referenceBuilder;
    }

    public void processReview(
        UUID teamId,
        SprintReview latestReview,
        List<SprintReview> rollingWindowReviews,
        List<EditLog> editLogs
    ) {
        if (teamId == null) {
            throw new IllegalArgumentException("teamId is required.");
        }
        if (latestReview == null) {
            throw new IllegalArgumentException("latestReview is required.");
        }

        preferenceService.updateTeamProfile(teamId, editLogs == null ? List.of() : editLogs);
        referenceBuilder.updateSprintHistory(teamId, rollingWindowReviews == null ? List.of(latestReview) : rollingWindowReviews);
    }
}
