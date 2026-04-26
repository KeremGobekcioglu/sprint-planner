package com.sprintplanner.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a software team using the sprint planner.
 * Pure domain object — no framework annotations.
 */
@Entity
@Table(name = "team")
public class Team {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** E_COMMERCE | FINTECH | GENERIC */
    @Column(nullable = false)
    private String domain;

    @Column(name = "sprint_length_days", nullable = false)
    private int sprintLengthDays;

    @Column(name = "trello_board_id")
    private String trelloBoardId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Developer> developers = new ArrayList<>();

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Sprint> sprints = new ArrayList<>();

    public Team() {}

    public Team(UUID id, String name, String domain, int sprintLengthDays,
                String trelloBoardId, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.domain = domain;
        this.sprintLengthDays = sprintLengthDays;
        this.trelloBoardId = trelloBoardId;
        this.createdAt = createdAt;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDomain() { return domain; }
    public int getSprintLengthDays() { return sprintLengthDays; }
    public String getTrelloBoardId() { return trelloBoardId; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Developer> getDevelopers() { return developers; }
    public List<Sprint> getSprints() { return sprints; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(UUID id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDomain(String domain) { this.domain = domain; }
    public void setSprintLengthDays(int sprintLengthDays) { this.sprintLengthDays = sprintLengthDays; }
    public void setTrelloBoardId(String trelloBoardId) { this.trelloBoardId = trelloBoardId; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setDevelopers(List<Developer> developers) { this.developers = developers; }
    public void setSprints(List<Sprint> sprints) { this.sprints = sprints; }
}
