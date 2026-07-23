package com.sprintplanner.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit log for edits made to sprint plans on the Review Screen.
 * Source of truth for regenerating team_profile.md.
 * Pure domain object — no framework annotations.
 */
@Entity
@Table(name = "edit_log")
public class EditLog {

    @Id
    private UUID id;

    @Column(name = "sprint_id", nullable = false)
    private UUID sprintId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id", insertable = false, updatable = false)
    private Sprint sprint;

    /** FK to the task that was edited — nullable for legacy rows. */
    @Column(name = "task_id")
    private UUID taskId;

    /** The field name that was edited (e.g. "assignedDevId", "storyPoints"). */
    @Column(nullable = false)
    private String field;

    @Column(name = "original_value", nullable = false)
    private String originalValue;

    @Column(name = "new_value", nullable = false)
    private String newValue;

    /** The type/category of the task that was edited (e.g. "backend", "frontend"). */
    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    public EditLog() {}

    public EditLog(UUID id, UUID sprintId, String field, String originalValue,
                   String newValue, String taskType, Instant loggedAt) {
        this.id = id;
        this.sprintId = sprintId;
        this.field = field;
        this.originalValue = originalValue;
        this.newValue = newValue;
        this.taskType = taskType;
        this.loggedAt = loggedAt;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getSprintId() { return sprintId; }
    public Sprint getSprint() { return sprint; }
    public UUID getTaskId() { return taskId; }
    public String getField() { return field; }
    public String getOriginalValue() { return originalValue; }
    public String getNewValue() { return newValue; }
    public String getTaskType() { return taskType; }
    public Instant getLoggedAt() { return loggedAt; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(UUID id) { this.id = id; }
    public void setSprintId(UUID sprintId) { this.sprintId = sprintId; }
    public void setSprint(Sprint sprint) { this.sprint = sprint; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public void setField(String field) { this.field = field; }
    public void setOriginalValue(String originalValue) { this.originalValue = originalValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public void setLoggedAt(Instant loggedAt) { this.loggedAt = loggedAt; }
}
