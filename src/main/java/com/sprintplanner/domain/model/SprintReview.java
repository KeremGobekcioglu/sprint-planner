package com.sprintplanner.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sprint retrospective / review data — one per sprint.
 * Pure domain object — no framework annotations.
 */
@Entity
@Table(name = "sprint_review")
public class SprintReview {

    public enum GoalResult {
        YES, PARTIALLY, NO
    }

    @Id
    private UUID id;

    @Column(name = "sprint_id", nullable = false)
    private UUID sprintId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id", insertable = false, updatable = false)
    private Sprint sprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_result", nullable = false)
    private GoalResult goalResult;

    @Column(name = "blocker_note")
    private String blockerNote;

    @Column(name = "total_planned_pts", nullable = false)
    private int totalPlannedPts;

    @Column(name = "total_completed_pts", nullable = false)
    private int totalCompletedPts;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "retrospective_report", columnDefinition = "TEXT")
    private String retrospectiveReport;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DevCompletion> devCompletions = new ArrayList<>();

    public SprintReview() {}

    public SprintReview(UUID id, UUID sprintId, GoalResult goalResult, String blockerNote,
                        int totalPlannedPts, int totalCompletedPts, Instant submittedAt) {
        this.id = id;
        this.sprintId = sprintId;
        this.goalResult = goalResult;
        this.blockerNote = blockerNote;
        this.totalPlannedPts = totalPlannedPts;
        this.totalCompletedPts = totalCompletedPts;
        this.submittedAt = submittedAt;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getSprintId() { return sprintId; }
    public Sprint getSprint() { return sprint; }
    public GoalResult getGoalResult() { return goalResult; }
    public String getBlockerNote() { return blockerNote; }
    public int getTotalPlannedPts() { return totalPlannedPts; }
    public int getTotalCompletedPts() { return totalCompletedPts; }
    public Instant getSubmittedAt() { return submittedAt; }
    public String getRetrospectiveReport() { return retrospectiveReport; }
    public List<DevCompletion> getDevCompletions() { return devCompletions; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(UUID id) { this.id = id; }
    public void setSprintId(UUID sprintId) { this.sprintId = sprintId; }
    public void setSprint(Sprint sprint) { this.sprint = sprint; }
    public void setGoalResult(GoalResult goalResult) { this.goalResult = goalResult; }
    public void setBlockerNote(String blockerNote) { this.blockerNote = blockerNote; }
    public void setTotalPlannedPts(int totalPlannedPts) { this.totalPlannedPts = totalPlannedPts; }
    public void setTotalCompletedPts(int totalCompletedPts) { this.totalCompletedPts = totalCompletedPts; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public void setRetrospectiveReport(String retrospectiveReport) { this.retrospectiveReport = retrospectiveReport; }
    public void setDevCompletions(List<DevCompletion> devCompletions) { this.devCompletions = devCompletions; }
}
