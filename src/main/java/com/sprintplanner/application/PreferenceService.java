package com.sprintplanner.application;

import com.sprintplanner.domain.model.EditLog;
import com.sprintplanner.domain.port.out.MemoryWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PreferenceService {

    private final MemoryWriter memoryWriter;

    public PreferenceService(MemoryWriter memoryWriter) {
        this.memoryWriter = memoryWriter;
    }

    public void updateTeamProfile(UUID teamId, List<EditLog> editLogs) {
        String markdown = buildTeamProfileMarkdown(editLogs == null ? List.of() : editLogs);
        memoryWriter.writeTeamProfile(teamId, markdown);
    }

    private String buildTeamProfileMarkdown(List<EditLog> editLogs) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Team Profile Preferences").append(System.lineSeparator())
            .append(System.lineSeparator())
            .append("Generated at: ").append(Instant.now()).append(System.lineSeparator())
            .append(System.lineSeparator());

        if (editLogs.isEmpty()) {
            builder.append("- No preference edits captured yet.").append(System.lineSeparator());
            return builder.toString();
        }

        builder.append("## Recent Review Edits").append(System.lineSeparator());
        for (EditLog log : editLogs) {
            builder.append("- ")
                .append(log.getField())
                .append(": ")
                .append(log.getOriginalValue())
                .append(" -> ")
                .append(log.getNewValue())
                .append(" (task type: ")
                .append(log.getTaskType())
                .append(")")
                .append(System.lineSeparator());
        }
        return builder.toString();
    }
}
