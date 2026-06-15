package com.sprintplanner.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class TeamMemoryId implements Serializable {

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "file_type")
    private String fileType;

    public TeamMemoryId() {}

    public TeamMemoryId(UUID teamId, String fileType) {
        this.teamId = teamId;
        this.fileType = fileType;
    }

    public UUID getTeamId() { return teamId; }
    public String getFileType() { return fileType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamMemoryId that)) return false;
        return Objects.equals(teamId, that.teamId) && Objects.equals(fileType, that.fileType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId, fileType);
    }
}
