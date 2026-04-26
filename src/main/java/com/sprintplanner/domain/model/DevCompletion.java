package com.sprintplanner.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Tracks how many story points a developer planned vs completed in a sprint review.
 * Pure domain object — no framework annotations.
 */
@Entity
@Table(name = "dev_completion")
public class DevCompletion {

    @Id
    private UUID id;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", insertable = false, updatable = false)
    private SprintReview review;

    @Column(name = "developer_id", nullable = false)
    private UUID developerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "developer_id", insertable = false, updatable = false)
    private Developer developer;

    @Column(name = "planned_pts", nullable = false)
    private int plannedPts;

    @Column(name = "completed_pts", nullable = false)
    private int completedPts;

    public DevCompletion() {}

    public DevCompletion(UUID id, UUID reviewId, UUID developerId,
                         int plannedPts, int completedPts) {
        this.id = id;
        this.reviewId = reviewId;
        this.developerId = developerId;
        this.plannedPts = plannedPts;
        this.completedPts = completedPts;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getReviewId() { return reviewId; }
    public SprintReview getReview() { return review; }
    public UUID getDeveloperId() { return developerId; }
    public Developer getDeveloper() { return developer; }
    public int getPlannedPts() { return plannedPts; }
    public int getCompletedPts() { return completedPts; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(UUID id) { this.id = id; }
    public void setReviewId(UUID reviewId) { this.reviewId = reviewId; }
    public void setReview(SprintReview review) { this.review = review; }
    public void setDeveloperId(UUID developerId) { this.developerId = developerId; }
    public void setDeveloper(Developer developer) { this.developer = developer; }
    public void setPlannedPts(int plannedPts) { this.plannedPts = plannedPts; }
    public void setCompletedPts(int completedPts) { this.completedPts = completedPts; }
}
