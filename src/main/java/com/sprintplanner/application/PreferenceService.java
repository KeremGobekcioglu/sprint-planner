package com.sprintplanner.application;

import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.EditLog;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.MemoryWriter;
import com.sprintplanner.domain.port.out.TeamRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PreferenceService {

    private final MemoryWriter memoryWriter;
    private final DeveloperRepository developerRepository;
    private final TeamRepository teamRepository;

    public PreferenceService(MemoryWriter memoryWriter, DeveloperRepository developerRepository, TeamRepository teamRepository) {
        this.memoryWriter = memoryWriter;
        this.developerRepository = developerRepository;
        this.teamRepository = teamRepository;
    }

    public void updateTeamProfile(UUID teamId, List<EditLog> editLogs) {
        String markdown = buildTeamProfileMarkdown(teamId, editLogs == null ? List.of() : editLogs);
        memoryWriter.writeTeamProfile(teamId, markdown);
    }

    private String buildTeamProfileMarkdown(UUID teamId, List<EditLog> editLogs) {
        // Get team name
        String teamName = teamRepository.findById(teamId)
            .map(com.sprintplanner.domain.model.Team::getName)
            .orElse("Unknown Team");

        StringBuilder builder = new StringBuilder();
        builder.append("# Team Preferences — ").append(teamName).append(System.lineSeparator())
            .append("Last updated: ")
            .append(DateTimeFormatter.ISO_DATE_TIME.format(Instant.now().atZone(ZoneId.systemDefault())))
            .append(System.lineSeparator())
            .append(System.lineSeparator());

        if (editLogs.isEmpty()) {
            builder.append("## Patterns Detected").append(System.lineSeparator())
                .append("- No edits available yet.").append(System.lineSeparator());
            return builder.toString();
        }

        // Detect patterns
        List<String> patterns = detectPatterns(editLogs);

        // Build Patterns section
        builder.append("## Patterns Detected").append(System.lineSeparator());
        if (patterns.isEmpty()) {
            builder.append("- No clear patterns detected from edits.").append(System.lineSeparator());
        } else {
            for (String pattern : patterns) {
                builder.append("- ").append(pattern).append(System.lineSeparator());
            }
        }
        builder.append(System.lineSeparator());

        // Raw Edit Summary
        builder.append("## Raw Edit Summary").append(System.lineSeparator())
            .append(editLogs.size()).append(" edits logged since last sprint").append(System.lineSeparator());

        return builder.toString();
    }

    private List<String> detectPatterns(List<EditLog> editLogs) {
        List<String> patterns = new ArrayList<>();

        // Rule 1: Deferred tasks (3+ times)
        long deferredCount = editLogs.stream()
            .filter(log -> "deferred".equals(log.getField()))
            .count();
        if (deferredCount >= 3) {
            patterns.add("PM frequently defers tasks — consider reducing scope per epic");
        }

        // Rule 2: Story Points increase (2+ times)
        long storyPointsIncreaseCount = editLogs.stream()
            .filter(log -> "storyPoints".equals(log.getField()))
            .filter(log -> {
                try {
                    int oldVal = Integer.parseInt(log.getOriginalValue());
                    int newVal = Integer.parseInt(log.getNewValue());
                    return newVal > oldVal;
                } catch (NumberFormatException e) {
                    return false;
                }
            })
            .count();
        if (storyPointsIncreaseCount >= 2) {
            patterns.add("Estimation tends to be low — consider raising default story points for tasks");
        }

        // Rule 3: Assign from unassigned (2+ times)
        long unmappedAssignCount = editLogs.stream()
            .filter(log -> "assignedDevId".equals(log.getField()))
            .filter(log -> "unassigned".equals(log.getOriginalValue()))
            .count();
        if (unmappedAssignCount >= 2) {
            patterns.add("PM manually assigns unassigned tasks — distribution agent may need better capability matching");
        }

        // Rule 4: Same developer receives reassignments (frequently)
        Map<UUID, Long> reassignmentCounts = new HashMap<>();
        editLogs.stream()
            .filter(log -> "assignedDevId".equals(log.getField()))
            .filter(log -> !"unassigned".equals(log.getOriginalValue())) // Reassignment, not initial assignment
            .forEach(log -> {
                try {
                    UUID devId = UUID.fromString(log.getNewValue());
                    reassignmentCounts.put(devId, reassignmentCounts.getOrDefault(devId, 0L) + 1);
                } catch (IllegalArgumentException e) {
                    // Not a valid UUID, skip
                }
            });

        // If any developer receives 2+ reassignments
        for (Map.Entry<UUID, Long> entry : reassignmentCounts.entrySet()) {
            if (entry.getValue() >= 2) {
                String devName = developerRepository.findById(entry.getKey())
                    .map(Developer::getName)
                    .orElse(entry.getKey().toString());
                patterns.add("Developer " + devName + " frequently receives reassigned tasks");
            }
        }

        return patterns;
    }
}
