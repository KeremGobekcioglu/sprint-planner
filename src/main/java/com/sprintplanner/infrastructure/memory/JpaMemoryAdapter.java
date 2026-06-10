package com.sprintplanner.infrastructure.memory;

import com.sprintplanner.domain.model.TeamMemory;
import com.sprintplanner.domain.model.TeamMemoryId;
import com.sprintplanner.domain.port.out.MemoryReader;
import com.sprintplanner.domain.port.out.MemoryWriter;
import com.sprintplanner.infrastructure.persistence.TeamMemoryJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class JpaMemoryAdapter implements MemoryReader, MemoryWriter {

    private static final String PROFILE = "profile";
    private static final String HISTORY = "history";

    private final TeamMemoryJpaRepository repo;

    public JpaMemoryAdapter(TeamMemoryJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public String readTeamProfile(UUID teamId) {
        return read(teamId, PROFILE);
    }

    @Override
    public String readSprintHistory(UUID teamId) {
        return read(teamId, HISTORY);
    }

    @Override
    public void writeTeamProfile(UUID teamId, String content) {
        write(teamId, PROFILE, content);
    }

    @Override
    public void writeSprintHistory(UUID teamId, String content) {
        write(teamId, HISTORY, content);
    }

    @Override
    public Instant getTeamProfileLastUpdated(UUID teamId) {
        return getLastUpdated(teamId, PROFILE);
    }

    @Override
    public Instant getSprintHistoryLastUpdated(UUID teamId) {
        return getLastUpdated(teamId, HISTORY);
    }

    private String read(UUID teamId, String fileType) {
        return repo.findById(new TeamMemoryId(teamId, fileType))
                .map(TeamMemory::getContent)
                .orElse("");
    }

    private Instant getLastUpdated(UUID teamId, String fileType) {
        return repo.findById(new TeamMemoryId(teamId, fileType))
                .map(TeamMemory::getUpdatedAt)
                .orElse(null);
    }

    private void write(UUID teamId, String fileType, String content) {
        TeamMemoryId id = new TeamMemoryId(teamId, fileType);
        TeamMemory memory = repo.findById(id)
                .orElse(new TeamMemory(id, ""));
        memory.setContent(content == null ? "" : content);
        repo.save(memory);
    }
}
