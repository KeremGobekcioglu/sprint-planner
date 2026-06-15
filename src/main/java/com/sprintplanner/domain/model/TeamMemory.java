package com.sprintplanner.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "team_memory")
public class TeamMemory {

    @EmbeddedId
    private TeamMemoryId id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TeamMemory() {}

    public TeamMemory(TeamMemoryId id, String content) {
        this.id = id;
        this.content = content;
        this.updatedAt = Instant.now();
    }

    public TeamMemoryId getId() { return id; }
    public String getContent() { return content; }

    public void setContent(String content) {
        this.content = content;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() { return updatedAt; }
}
