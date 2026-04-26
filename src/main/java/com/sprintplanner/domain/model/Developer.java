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
 * Represents a developer on a team.
 * Pure domain object — no framework annotations.
 */
@Entity
@Table(name = "developer")
public class Developer {

    @Id
    private UUID id;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", insertable = false, updatable = false)
    private Team team;

    @Column(nullable = false)
    private String name;

    /** backend | frontend | qa | devops */
    @Column(nullable = false)
    private String role;

    /** Base story points per sprint (before capacity adjustment). */
    @Column(name = "base_velocity", nullable = false)
    private int baseVelocity;

    public Developer() {}

    public Developer(UUID id, UUID teamId, String name, String role, int baseVelocity) {
        this.id = id;
        this.teamId = teamId;
        this.name = name;
        this.role = role;
        this.baseVelocity = baseVelocity;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getTeamId() { return teamId; }
    public Team getTeam() { return team; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public int getBaseVelocity() { return baseVelocity; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(UUID id) { this.id = id; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }
    public void setTeam(Team team) { this.team = team; }
    public void setName(String name) { this.name = name; }
    public void setRole(String role) { this.role = role; }
    public void setBaseVelocity(int baseVelocity) { this.baseVelocity = baseVelocity; }
}
