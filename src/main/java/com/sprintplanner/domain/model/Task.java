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
 * An individual task within a user story.
 * Pure domain object — no framework annotations.
 */
@Entity
@Table(name = "task")
public class Task {

    @Id
    private UUID id;

    @Column(name = "story_id", nullable = false)
    private UUID storyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", insertable = false, updatable = false)
    private UserStory story;

    /** FK to Developer — nullable until distribution is complete. */
    @Column(name = "assigned_dev_id")
    private UUID assignedDevId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_dev_id", insertable = false, updatable = false)
    private Developer assignedDeveloper;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "acceptance_criterion", nullable = false)
    private String acceptanceCriterion;

    /** backend | frontend | qa | devops — used for routing during distribution */
    @Column(name = "role_tag", nullable = false)
    private String roleTag;

    /** Fibonacci: 1, 2, 3, 5, 8, 13 */
    @Column(name = "story_points", nullable = false)
    private int storyPoints;

    @Column(nullable = false)
    private String status;

    /** Trello card ID — populated after publishing. */
    @Column(name = "trello_card_id")
    private String trelloCardId;

    public Task() {}

    public Task(UUID id, UUID storyId, UUID assignedDevId, String title, String description,
                String acceptanceCriterion, String roleTag, int storyPoints,
                String status, String trelloCardId) {
        this.id = id;
        this.storyId = storyId;
        this.assignedDevId = assignedDevId;
        this.title = title;
        this.description = description;
        this.acceptanceCriterion = acceptanceCriterion;
        this.roleTag = roleTag;
        this.storyPoints = storyPoints;
        this.status = status;
        this.trelloCardId = trelloCardId;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getStoryId() { return storyId; }
    public UserStory getStory() { return story; }
    public UUID getAssignedDevId() { return assignedDevId; }
    public Developer getAssignedDeveloper() { return assignedDeveloper; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAcceptanceCriterion() { return acceptanceCriterion; }
    public String getRoleTag() { return roleTag; }
    public int getStoryPoints() { return storyPoints; }
    public String getStatus() { return status; }
    public String getTrelloCardId() { return trelloCardId; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(UUID id) { this.id = id; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }
    public void setStory(UserStory story) { this.story = story; }
    public void setAssignedDevId(UUID assignedDevId) { this.assignedDevId = assignedDevId; }
    public void setAssignedDeveloper(Developer assignedDeveloper) { this.assignedDeveloper = assignedDeveloper; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setAcceptanceCriterion(String acceptanceCriterion) { this.acceptanceCriterion = acceptanceCriterion; }
    public void setRoleTag(String roleTag) { this.roleTag = roleTag; }
    public void setStoryPoints(int storyPoints) { this.storyPoints = storyPoints; }
    public void setStatus(String status) { this.status = status; }
    public void setTrelloCardId(String trelloCardId) { this.trelloCardId = trelloCardId; }
}
