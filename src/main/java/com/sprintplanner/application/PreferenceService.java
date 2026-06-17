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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PreferenceService {

    /** Patterns not re-detected within this window are dropped from the profile. */
    private static final long PATTERN_DECAY_DAYS = 60;

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

        // Load previous patterns with their last-seen timestamps
        String existingProfile = memoryReader.readTeamProfile(teamId);
        Map<String, Instant> previousPatterns = parseExistingPatterns(existingProfile);

        // Detect patterns from current sprint's edit logs
        List<String> newPatterns = editLogs.isEmpty() ? List.of() : detectPatterns(editLogs);

        Instant now = Instant.now();
        Instant decayCutoff = now.minus(PATTERN_DECAY_DAYS, ChronoUnit.DAYS);

        // Merge: refresh last-seen for re-detected patterns; drop stale ones; add new ones
        Map<String, Instant> mergedPatterns = new LinkedHashMap<>();
        for (Map.Entry<String, Instant> entry : previousPatterns.entrySet()) {
            if (newPatterns.contains(entry.getKey())) {
                mergedPatterns.put(entry.getKey(), now);
            } else if (entry.getValue().isAfter(decayCutoff)) {
                mergedPatterns.put(entry.getKey(), entry.getValue());
            }
            // else: pattern is stale — drop it
        }
        for (String p : newPatterns) {
            mergedPatterns.putIfAbsent(p, now);
        }

        builder.append("## Patterns Detected").append(System.lineSeparator());
        if (mergedPatterns.isEmpty()) {
            builder.append("- No clear patterns detected from edits.").append(System.lineSeparator());
        } else {
            for (Map.Entry<String, Instant> entry : mergedPatterns.entrySet()) {
                String lastSeenDate = DateTimeFormatter.ISO_LOCAL_DATE
                    .format(entry.getValue().atZone(ZoneId.systemDefault()));
                builder.append("- ").append(entry.getKey())
                    .append(" (last seen: ").append(lastSeenDate).append(")")
                    .append(System.lineSeparator());
            }
        }
        builder.append(System.lineSeparator());

        builder.append("## Raw Edit Summary").append(System.lineSeparator())
            .append(editLogs.size()).append(" edits logged since last sprint").append(System.lineSeparator());

        return builder.toString();
    }

    /**
     * Parses the ## Patterns Detected section from a stored profile.
     * Returns a map of pattern-text → last-seen instant.
     * Tolerates old-format lines with no "(last seen: ...)" suffix — treats them as seen now
     * so an existing profile is not wiped on the first run after deploy.
     */
    private Map<String, Instant> parseExistingPatterns(String existingProfile) {
        Map<String, Instant> patterns = new LinkedHashMap<>();
        if (existingProfile == null || existingProfile.isBlank()) {
            return patterns;
        }
        Instant now = Instant.now();
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
                    String raw = line.substring(2).trim();
                    if (raw.isEmpty()
                            || raw.equals("No edits available yet.")
                            || raw.equals("No clear patterns detected from edits.")) {
                        continue;
                    }
                    // Parse optional "(last seen: YYYY-MM-DD)" suffix
                    int suffixStart = raw.lastIndexOf(" (last seen: ");
                    if (suffixStart >= 0 && raw.endsWith(")")) {
                        String patternText = raw.substring(0, suffixStart).trim();
                        String dateStr = raw.substring(suffixStart + " (last seen: ".length(), raw.length() - 1);
                        Instant lastSeen;
                        try {
                            lastSeen = java.time.LocalDate.parse(dateStr)
                                .atStartOfDay(ZoneId.systemDefault()).toInstant();
                        } catch (Exception e) {
                            lastSeen = now; // malformed date — treat as current
                        }
                        patterns.put(patternText, lastSeen);
                    } else {
                        // Old-format line with no suffix — treat as seen now to avoid wiping on migration
                        patterns.put(raw, now);
                    }
                }
            }
        }
        return patterns;
    }

    private List<String> detectPatterns(List<EditLog> editLogs) {
        List<String> patterns = new ArrayList<>();

        // Count only defer-ON edits (newValue == "true"). Defer-then-undefer would otherwise
        // inflate the count. Per-task net dedup is not possible because entityId is not
        // persisted on EditLog — this "true"-filter is the chosen approximation.
        long deferredCount = editLogs.stream()
            .filter(log -> "deferred".equals(log.getField()))
            .filter(log -> "true".equals(log.getNewValue()))
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
