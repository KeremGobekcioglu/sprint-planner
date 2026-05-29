package com.sprintplanner.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents an application user
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String role;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "trello_token")
    private String trelloToken;

    public AppUser() {}

    public AppUser(UUID id, String email, String name, String role, String passwordHash, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.role = role;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public String getTrelloToken() { return trelloToken; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(UUID id) { this.id = id; }
    public void setEmail(String email) { this.email = email; }
    public void setName(String name) { this.name = name; }
    public void setRole(String role) { this.role = role; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setTrelloToken(String trelloToken) { this.trelloToken = trelloToken; }
}

