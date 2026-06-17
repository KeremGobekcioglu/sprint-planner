package com.sprintplanner.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.ArrayList;
import java.util.List;
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

    /** backend | frontend | qa | devops | android | ios | fullstack | ai | custom */
    @Column(nullable = false)
    private String role;

    /** JUNIOR | MID | SENIOR — used for seniority-aware task distribution. */
    @Column(nullable = false)
    private String seniority = "MID";

    @Column(name = "capabilities")
    private String capabilities;

    /** Base story points per sprint (before capacity adjustment). */
    @Column(name = "base_velocity", nullable = false)
    private int baseVelocity;

    /** Trello member ID — only set if imported from Trello board. */
    @Column(name = "trello_member_id")
    private String trelloMemberId;

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
    public String getSeniority() { return seniority != null ? seniority : "MID"; }
    public int getBaseVelocity() { return baseVelocity; }
    public String getCapabilitiesRaw() { return capabilities; }

    @Transient
    public List<String> getCapabilities() {
        if (capabilities == null || capabilities.isBlank()) {
            return List.of();
        }
        String[] tokens = capabilities.split(",");
        List<String> values = new ArrayList<>();
        for (String token : tokens) {
            String normalized = normalizeCapability(token);
            if (!normalized.isBlank() && !values.contains(normalized)) {
                values.add(normalized);
            }
        }
        return values;
    }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(UUID id) { this.id = id; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }
    public void setTeam(Team team) { this.team = team; }
    public void setName(String name) { this.name = name; }
    public void setRole(String role) { this.role = role; }
    public void setSeniority(String seniority) { this.seniority = seniority != null ? seniority.toUpperCase() : "MID"; }
    public void setBaseVelocity(int baseVelocity) { this.baseVelocity = baseVelocity; }
    public String getTrelloMemberId() { return trelloMemberId; }
    public void setTrelloMemberId(String trelloMemberId) { this.trelloMemberId = trelloMemberId; }

    public void setCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            this.capabilities = null;
            return;
        }
        List<String> normalized = new ArrayList<>();
        for (String value : capabilities) {
            String normalizedValue = normalizeCapability(value);
            if (!normalizedValue.isBlank() && !normalized.contains(normalizedValue)) {
                normalized.add(normalizedValue);
            }
        }
        this.capabilities = normalized.isEmpty() ? null : String.join(",", normalized);
    }

    private String normalizeCapability(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }
}
