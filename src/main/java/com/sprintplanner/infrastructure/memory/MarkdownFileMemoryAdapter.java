package com.sprintplanner.infrastructure.memory;

import com.sprintplanner.domain.port.out.MemoryReader;
import com.sprintplanner.domain.port.out.MemoryWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MarkdownFileMemoryAdapter implements MemoryReader, MemoryWriter {

    private static final String TEAM_PROFILE_FILE = "team_profile.md";
    private static final String SPRINT_HISTORY_FILE = "sprint_history.md";

    private final Path memoryRoot;

    public MarkdownFileMemoryAdapter(@Value("${sprintplanner.memory.base-path:memory}") String memoryBasePath) {
        this.memoryRoot = Path.of(memoryBasePath);
    }

    @Override
    public String readTeamProfile(UUID teamId) {
        return readMemoryFile(teamId, TEAM_PROFILE_FILE);
    }

    @Override
    public String readSprintHistory(UUID teamId) {
        return readMemoryFile(teamId, SPRINT_HISTORY_FILE);
    }

    @Override
    public void writeTeamProfile(UUID teamId, String content) {
        writeMemoryFile(teamId, TEAM_PROFILE_FILE, content);
    }

    @Override
    public void writeSprintHistory(UUID teamId, String content) {
        writeMemoryFile(teamId, SPRINT_HISTORY_FILE, content);
    }

    private String readMemoryFile(UUID teamId, String fileName) {
        Path filePath = resolveTeamFilePath(teamId, fileName);
        if (!Files.exists(filePath)) {
            return "";
        }
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read memory file: " + filePath, e);
        }
    }

    private void writeMemoryFile(UUID teamId, String fileName, String content) {
        Path filePath = resolveTeamFilePath(teamId, fileName);
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write memory file: " + filePath, e);
        }
    }

    private Path resolveTeamFilePath(UUID teamId, String fileName) {
        return memoryRoot.resolve(teamId.toString()).resolve(fileName);
    }
}
