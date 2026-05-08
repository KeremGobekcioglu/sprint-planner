package com.sprintplanner.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.NamedSubgraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a planning sprint for a team.
 * Pure domain object — no framework annotations.
 */
@Entity
@NamedEntityGraph(
    name = "Sprint.withUserStoriesAndTasks",
    attributeNodes = @NamedAttributeNode(value = "userStories", subgraph = "userStoriesWithTasks"),
    subgraphs = @NamedSubgraph(
        name = "userStoriesWithTasks",
        attributeNodes = @NamedAttributeNode("tasks")
    )
)
@Table(name = "sprint")
public class Sprint {

    public enum Status {
        PLANNING, IN_REVIEW, ACTIVE, COMPLETED
    }

    @Id
    private UUID id;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", insertable = false, updatable = false)
    private Team team;

    @Column(name = "sprint_number", nullable = false)
    private int sprintNumber;

    @Column(nullable = false)
    private String goal;

    /** Overrides team domain for this sprint if set. */
    @Column(name = "domain_override")
    private String domainOverride;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "planned_at", nullable = false)
    private Instant plannedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @OneToMany(mappedBy = "sprint", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<UserStory> userStories = new HashSet<>();

    @OneToOne(mappedBy = "sprint", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private SprintReview sprintReview;

    @OneToMany(mappedBy = "sprint", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EditLog> editLogs = new ArrayList<>();

    public Sprint() {}

    public Sprint(UUID id, UUID teamId, int sprintNumber, String goal,
                  String domainOverride, Status status, Instant plannedAt, Instant reviewedAt) {
        this.id = id;
        this.teamId = teamId;
        this.sprintNumber = sprintNumber;
        this.goal = goal;
        this.domainOverride = domainOverride;
        this.status = status;
        this.plannedAt = plannedAt;
        this.reviewedAt = reviewedAt;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getTeamId() { return teamId; }
    public Team getTeam() { return team; }
    public int getSprintNumber() { return sprintNumber; }
    public String getGoal() { return goal; }
    public String getDomainOverride() { return domainOverride; }
    public Status getStatus() { return status; }
    public Instant getPlannedAt() { return plannedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Set<UserStory> getUserStories() { return userStories; }
    public SprintReview getSprintReview() { return sprintReview; }
    public List<EditLog> getEditLogs() { return editLogs; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(UUID id) { this.id = id; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }
    public void setTeam(Team team) { this.team = team; }
    public void setSprintNumber(int sprintNumber) { this.sprintNumber = sprintNumber; }
    public void setGoal(String goal) { this.goal = goal; }
    public void setDomainOverride(String domainOverride) { this.domainOverride = domainOverride; }
    public void setStatus(Status status) { this.status = status; }
    public void setPlannedAt(Instant plannedAt) { this.plannedAt = plannedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public void setUserStories(Set<UserStory> userStories) { this.userStories = userStories; }
    public void setSprintReview(SprintReview sprintReview) { this.sprintReview = sprintReview; }
    public void setEditLogs(List<EditLog> editLogs) { this.editLogs = editLogs; }
}
