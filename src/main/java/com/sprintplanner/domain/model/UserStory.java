package com.sprintplanner.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A user story belonging to a sprint.
 * Pure domain object — no framework annotations.
 */
@Entity
@Table(name = "user_story")
public class UserStory {

    @Id
    private UUID id;

    @Column(name = "sprint_id", nullable = false)
    private UUID sprintId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id", insertable = false, updatable = false)
    private Sprint sprint;

    @Column(name = "epic_title", nullable = false)
    private String epicTitle;

    @Column(nullable = false)
    private String title;

    @Column(name = "story_points", nullable = false)
    private int storyPoints;

    @Column(nullable = false)
    private String status;

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderColumn(name = "task_order")
    private List<Task> tasks = new ArrayList<>();

    public UserStory() {}

    public UserStory(UUID id, UUID sprintId, String epicTitle, String title,
                     int storyPoints, String status) {
        this.id = id;
        this.sprintId = sprintId;
        this.epicTitle = epicTitle;
        this.title = title;
        this.storyPoints = storyPoints;
        this.status = status;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getSprintId() { return sprintId; }
    public Sprint getSprint() { return sprint; }
    public String getEpicTitle() { return epicTitle; }
    public String getTitle() { return title; }
    public int getStoryPoints() { return storyPoints; }
    public String getStatus() { return status; }
    public List<Task> getTasks() { return tasks; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(UUID id) { this.id = id; }
    public void setSprintId(UUID sprintId) { this.sprintId = sprintId; }
    public void setSprint(Sprint sprint) { this.sprint = sprint; }
    public void setEpicTitle(String epicTitle) { this.epicTitle = epicTitle; }
    public void setTitle(String title) { this.title = title; }
    public void setStoryPoints(int storyPoints) { this.storyPoints = storyPoints; }
    public void setStatus(String status) { this.status = status; }
    public void setTasks(List<Task> tasks) { this.tasks = tasks; }
}
