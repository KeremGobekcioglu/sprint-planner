package com.sprintplanner.application;

import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.EditLog;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.MemoryReader;
import com.sprintplanner.domain.port.out.MemoryWriter;
import com.sprintplanner.domain.port.out.TeamRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PreferenceService {

    private final MemoryWriter memoryWriter;
    private final MemoryReader memoryReader;
    private final DeveloperRepository developerRepository;
    private final TeamRepository teamRepository;

    public PreferenceService(MemoryWriter memoryWriter, MemoryReader memoryReader,
            DeveloperRepository developerRepository, TeamRepository teamRepository) {
        this.memoryWriter = memoryWriter;
        this.memoryReader = memoryReader;
        this.developerRepository = developerRepository;
        this.teamRepository = teamRepository;
    }

    public void updateTeamProfile(UUID teamId, List<EditLog> editLogs) {
        String markdown = buildTeamProfileMarkdown(teamId, editLogs == null ? List.of() : editLogs);
        memoryWriter.writeTeamProfile(teamId, markdown);
    }

    private String buildTeamProfileMarkdown(UUID teamId, List<EditLog> editLogs) {
        String teamName = teamRepository.findById(teamId)
            .map(com.sprintplanner.domain.model.Team::getName)
            .orElse("Unknown Team");

        StringBuilder builder = new StringBuilder();
        builder.append("# Team Preferences — ").append(teamName).append(System.lineSeparator())
            .append("Last updated: ")
            .append(DateTimeFormatter.ISO_DATE_TIME.format(Instant.now().atZone(ZoneId.systemDefault())))
            .append(System.lineSeparator())
            .append(System.lineSeparator());

        // Load and parse patterns from the previous profile
        String existingProfile = memoryReader.readTeamProfile(teamId);
        List<String> previousPatterns = parseExistingPatterns(existingProfile);

        // Detect patterns from current sprint's edit logs
        List<String> newPatterns = editLogs.isEmpty() ? List.of() : detectPatterns(editLogs);

        // Merge: start with previous patterns, add new ones that aren't already present
        LinkedHashSet<String> mergedPatterns = new LinkedHashSet<>(previousPatterns);
        for (String p : newPatterns) {
            mergedPatterns.add(p);
        }

        builder.append("## Patterns Detected").append(System.lineSeparator());
        if (mergedPatterns.isEmpty()) {
            builder.append("- No clear patterns detected from edits.").append(System.lineSeparator());
        } else {
            for (String pattern : mergedPatterns) {
                builder.append("- ").append(pattern).append(System.lineSeparator());
            }
        }
        builder.append(System.lineSeparator());

        builder.append("## Raw Edit Summary").append(System.lineSeparator())
            .append(editLogs.size()).append(" edits logged since last sprint").append(System.lineSeparator());

        return builder.toString();
    }

    private List<String> parseExistingPatterns(String existingProfile) {
        List<String> patterns = new ArrayList<>();
        if (existingProfile == null || existingProfile.isBlank()) {
            return patterns;
        }
        boolean inPatternsSection = false;
        for (String line : existingProfile.lines().toList()) {
            if (line.startsWith("## Patterns Detected")) {
                inPatternsSection = true;
                continue;
            }
            if (inPatternsSection) {
                if (line.startsWith("## ")) {
                    break;
                }
                if (line.startsWith("- ") && line.length() > 2) {
                    String pattern = line.substring(2).trim();
                    if (!pattern.isEmpty()
                            && !pattern.equals("No edits available yet.")
                            && !pattern.equals("No clear patterns detected from edits.")) {
                        patterns.add(pattern);
                    }
                }
            }
        }
        return patterns;
    }

    private List<String> detectPatterns(List<EditLog> editLogs) {
        List<String> patterns = new ArrayList<>();

        long deferredCount = editLogs.stream()
            .filter(log -> "deferred".equals(log.getField()))
            .count();
        if (deferredCount >= 3) {
            patterns.add("PM frequently defers tasks — consider reducing scope per epic");
        }

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

        long unmappedAssignCount = editLogs.stream()
            .filter(log -> "assignedDevId".equals(log.getField()))
            .filter(log -> "unassigned".equals(log.getOriginalValue()))
            .count();
        if (unmappedAssignCount >= 2) {
            patterns.add("PM manually assigns unassigned tasks — distribution agent may need better capability matching");
        }

        Map<UUID, Long> reassignmentCounts = new HashMap<>();
        editLogs.stream()
            .filter(log -> "assignedDevId".equals(log.getField()))
            .filter(log -> !"unassigned".equals(log.getOriginalValue()))
            .forEach(log -> {
                try {
                    UUID devId = UUID.fromString(log.getNewValue());
                    reassignmentCounts.put(devId, reassignmentCounts.getOrDefault(devId, 0L) + 1);
                } catch (IllegalArgumentException e) {
                    // skip non-UUID values
                }
            });

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
