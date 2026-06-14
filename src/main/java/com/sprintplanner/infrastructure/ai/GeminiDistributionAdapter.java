package com.sprintplanner.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.dto.SprintPlanMapper;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.out.DistributionPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Primary
public class GeminiDistributionAdapter implements DistributionPort {

    private static final Logger logger = LoggerFactory.getLogger(GeminiDistributionAdapter.class);
    private static final int SOLE_CAPABILITY_OVERFLOW_BUFFER_PTS = 2;

    private final GeminiClientProvider gemini;
    private final ObjectMapper objectMapper;

    public GeminiDistributionAdapter(GeminiClientProvider gemini, ObjectMapper objectMapper) {
        this.gemini = gemini;
        this.objectMapper = objectMapper;
    }

    @Override
    public SprintPlan distribute(
            UUID teamId,
            String sprintGoal,
            List<UserStory> estimatedUserStories,
            Map<UUID, Integer> capacityByDeveloper,
            Map<UUID, List<String>> developerCapabilities,
            Map<UUID, String> developerNames,
            String teamProfileMemory,
            String sprintHistoryMemory
    ) {
        if (teamProfileMemory != null && !teamProfileMemory.isBlank()) {
            logger.info("[DISTRIBUTION] Team profile memory loaded ({} chars): ...{}",
                teamProfileMemory.length(),
                teamProfileMemory.substring(0, Math.min(200, teamProfileMemory.length())));
        } else {
            logger.info("[DISTRIBUTION] No team profile memory — fresh start mode");
        }

        if (sprintHistoryMemory != null && !sprintHistoryMemory.isBlank()) {
            logger.info("[DISTRIBUTION] Sprint history memory loaded ({} chars)", sprintHistoryMemory.length());
        } else {
            logger.info("[DISTRIBUTION] No sprint history memory");
        }

        logger.info("[DISTRIBUTION] Developer capabilities: {}",
            developerCapabilities == null ? "null" :
            developerCapabilities.entrySet().stream()
                .map(e -> {
                    String name = developerNames != null
                        ? developerNames.getOrDefault(e.getKey(), e.getKey().toString().substring(0, 8))
                        : e.getKey().toString().substring(0, Math.min(8, e.getKey().toString().length()));
                    return name + "=" + e.getValue();
                })
                .collect(Collectors.joining(", ")));

        if (estimatedUserStories == null || estimatedUserStories.isEmpty()) {
            return new SprintPlan(teamId, sprintGoal, List.of(), List.of(), null, null);
        }
        if (capacityByDeveloper == null || capacityByDeveloper.isEmpty()) {
            return convertToSprintPlan(teamId, sprintGoal, estimatedUserStories, List.of());
        }

        String prompt = buildPrompt(
                estimatedUserStories, capacityByDeveloper, developerCapabilities,
                developerNames, teamProfileMemory, sprintHistoryMemory
        );

        Schema schema = buildDistributionSchema();
        String raw = gemini.generate(prompt, 0.3f, GeminiClientProvider.MODEL_DISTRIBUTION, schema);

        Map<UUID, Integer> remaining = new LinkedHashMap<>(capacityByDeveloper);

        List<Task> unassignedTasks = applyAssignments(
            estimatedUserStories,
            remaining,
            developerCapabilities,
            raw
        );

        // Validate and rebalance after distribution
        List<Task> rebalanced = validateAndRebalance(estimatedUserStories, remaining, developerCapabilities);
        unassignedTasks.addAll(rebalanced);

        // Log summary
        int totalTasks = estimatedUserStories.stream()
            .mapToInt(s -> s.getTasks() == null ? 0 : s.getTasks().size()).sum();
        int assignedCount = totalTasks - unassignedTasks.size();
        logger.info("[DISTRIBUTION] Final: {}/{} tasks assigned, {} unassigned",
            assignedCount, totalTasks, unassignedTasks.size());

        // Log per-developer utilization
        for (Map.Entry<UUID, Integer> entry : remaining.entrySet()) {
            int original = capacityByDeveloper.getOrDefault(entry.getKey(), 0);
            int used = original - entry.getValue();
            logger.info("[DISTRIBUTION] Dev {}: {}/{} pts used ({}%)",
                entry.getKey().toString().substring(0, 8),
                used, original,
                original == 0 ? 0 : Math.round((double) used / original * 100));
        }

        return convertToSprintPlan(teamId, sprintGoal, estimatedUserStories, unassignedTasks);
    }

    private SprintPlan convertToSprintPlan(UUID teamId, String sprintGoal, List<UserStory> userStories, List<Task> unassignedTasks) {
        Set<UUID> unassignedIds = (unassignedTasks == null || unassignedTasks.isEmpty())
            ? Set.of()
            : unassignedTasks.stream().map(Task::getId)
                .collect(Collectors.toSet());

        List<com.sprintplanner.domain.dto.UserStoryDto> storyDtos = userStories.stream()
            .map(story -> {
                List<com.sprintplanner.domain.dto.TaskDto> filteredTasks = story.getTasks() == null
                    ? List.of()
                    : story.getTasks().stream()
                        .filter(t -> !unassignedIds.contains(t.getId()))
                        .map(SprintPlanMapper::toDto)
                        .toList();
                return new com.sprintplanner.domain.dto.UserStoryDto(
                    story.getId(), story.getSprintId(), story.getEpicTitle(),
                    story.getTitle(), story.getStoryPoints(), story.getStatus(), filteredTasks
                );
            })
            .toList();

        return new SprintPlan(
            teamId,
            sprintGoal,
            storyDtos,
            unassignedTasks == null
                ? List.of()
                : unassignedTasks.stream().map(SprintPlanMapper::toDto).toList(),
            null,
            null
        );
    }

    // ── Prompt ───────────────────────────────────────────────────────────────

    private String buildPrompt(
            List<UserStory> stories,
            Map<UUID, Integer> capacityByDeveloper,
            Map<UUID, List<String>> developerCapabilities,
            Map<UUID, String> developerNames,
            String teamProfile,
            String sprintHistory
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert sprint planner. Rank developers by fit for each task.\n\n");

        sb.append("## Developers and their capacity\n");
        for (Map.Entry<UUID, Integer> entry : capacityByDeveloper.entrySet()) {
            List<String> capabilities = developerCapabilities == null
                ? List.of()
                : developerCapabilities.getOrDefault(entry.getKey(), List.of());
            String name = developerNames != null
                ? developerNames.getOrDefault(entry.getKey(), entry.getKey().toString())
                : entry.getKey().toString();
            sb.append("- ").append(name)
                .append(" (developerId: ").append(entry.getKey()).append(")")
                .append(" | capabilities: ").append(capabilities.isEmpty() ? "none" : String.join(", ", capabilities))
                .append(" | capacity: ").append(entry.getValue()).append(" pts\n");
        }
        sb.append("\n");

        if (sprintHistory != null && !sprintHistory.isBlank()) {
            sb.append("## Developer Performance Notes\n");
            sb.append("Use this to prefer developers with better track records for task types:\n");
            sb.append(sprintHistory).append("\n\n");
        }

        sb.append("## Tasks to rank\n");
        for (UserStory story : stories) {
            if (story.getTasks() == null) continue;
            for (Task task : story.getTasks()) {
                sb.append("- taskId: ").append(task.getId()).append("\n");
                sb.append("  title: ").append(task.getTitle()).append("\n");
                sb.append("  roleTag: ").append(task.getRoleTag()).append("\n");
                sb.append("  storyPoints: ").append(task.getStoryPoints()).append("\n\n");
            }
        }

        if (teamProfile != null && !teamProfile.isBlank()) {
            sb.append("## Team Preferences (use for ranking)\n")
                    .append(teamProfile).append("\n\n");
        }

        sb.append("## Output Rules\n");
        sb.append("Respond ONLY with a valid JSON array. No explanation, no markdown, no backticks.\n");
        sb.append("For each task, return a ranked list of developer preferences — best fit first.\n");
        sb.append("Each element:\n");
        sb.append("{ \"taskId\": \"<uuid>\", \"preferredDevOrder\": [\"<devId1>\", \"<devId2>\", ...] }\n");
        sb.append("Include ALL developer IDs that have matching capability, ranked by fit.\n");
        sb.append("If NO developer has matching capability, return: { \"taskId\": \"<uuid>\", \"preferredDevOrder\": [] }\n");
        sb.append("Do NOT make capacity decisions — just rank by fit. The system enforces capacity.\n\n");

        sb.append("## Example Output\n");
        sb.append("""
            [
              { "taskId": "aaa-111", "preferredDevOrder": ["dev-001", "dev-002"] },
              { "taskId": "bbb-222", "preferredDevOrder": ["dev-002"] },
              { "taskId": "ccc-333", "preferredDevOrder": [] }
            ]
            """);

        return sb.toString();
    }

    // ── Deterministic assignment ──────────────────────────────────────────────

    private List<Task> applyAssignments(
            List<UserStory> stories,
            Map<UUID, Integer> remaining,
            Map<UUID, List<String>> developerCapabilities,
            String raw
    ) {
        Map<String, Task> taskById = new HashMap<>();
        for (UserStory story : stories) {
            if (story.getTasks() == null) continue;
            for (Task task : story.getTasks()) {
                taskById.put(task.getId().toString(), task);
            }
        }

        List<Task> unassignedTasks = new ArrayList<>();

        // Parse LLM preference rankings
        Map<String, List<UUID>> preferencesByTaskId = parseLlmPreferences(raw, remaining.keySet());

        // Sort by capability exclusivity first (rarest capability → fewest devs), then by points
        List<Task> allTasks = new ArrayList<>(taskById.values());
        allTasks.sort(Comparator
            .comparingInt((Task t) -> countDevelopersWithCapability(
                normalizeRole(t.getRoleTag()), developerCapabilities))  // ascending — rarest first
            .thenComparingInt(Task::getStoryPoints).reversed()          // then heaviest first
        );

        for (Task task : allTasks) {
            String taskIdStr = task.getId().toString();
            List<UUID> preferredDevs = preferencesByTaskId.getOrDefault(taskIdStr, List.of());

            UUID assigned = findBestDeveloper(task, preferredDevs, remaining, developerCapabilities, allTasks);

            if (assigned != null) {
                task.setAssignedDevId(assigned);
                remaining.merge(assigned, -task.getStoryPoints(), Integer::sum);
                task.setAssignmentReason(buildReason(task, assigned, preferredDevs));
            } else {
                task.setAssignedDevId(null);
                task.setAssignmentReason(buildUnassignedReason(task, developerCapabilities));
                unassignedTasks.add(task);
            }
        }

        // Second pass: fill remaining capacity gaps with smallest unassigned tasks first
        List<Task> stillUnassigned = new ArrayList<>();
        List<Task> secondPassTasks = new ArrayList<>(unassignedTasks);
        secondPassTasks.sort(Comparator
            .comparingInt((Task t) -> countDevelopersWithCapability(
                normalizeRole(t.getRoleTag()), developerCapabilities))  // ascending — rarest first
            .thenComparingInt(Task::getStoryPoints).reversed()          // then heaviest first
        );

        for (Task task : secondPassTasks) {
            List<UUID> prefs = preferencesByTaskId.getOrDefault(task.getId().toString(), List.of());
            UUID assigned = findBestDeveloper(task, prefs, remaining, developerCapabilities, allTasks);
            if (assigned != null) {
                task.setAssignedDevId(assigned);
                remaining.merge(assigned, -task.getStoryPoints(), Integer::sum);
                task.setAssignmentReason(buildReason(task, assigned, prefs) + " (gap fill)");
            } else {
                stillUnassigned.add(task);
            }
        }
        return stillUnassigned;
    }

    private Map<String, List<UUID>> parseLlmPreferences(String raw, Set<UUID> validDevIds) {
        Map<String, List<UUID>> result = new HashMap<>();
        try {
            String cleaned = raw.trim()
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();
            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isArray()) return result;

            Set<String> validDevIdStrings = validDevIds.stream()
                .map(UUID::toString).collect(Collectors.toSet());

            for (JsonNode node : root) {
                String taskId = node.path("taskId").asText();
                List<UUID> prefs = new ArrayList<>();
                for (JsonNode devNode : node.path("preferredDevOrder")) {
                    String devIdStr = devNode.asText();
                    if (validDevIdStrings.contains(devIdStr)) {
                        prefs.add(UUID.fromString(devIdStr));
                    }
                }
                result.put(taskId, prefs);
            }
        } catch (Exception e) {
            logger.warn("[DISTRIBUTION] Failed to parse LLM preferences, falling back to capability-only assignment: {}", e.getMessage());
        }
        return result;
    }

    private UUID findBestDeveloper(
            Task task,
            List<UUID> preferredDevs,
            Map<UUID, Integer> remaining,
            Map<UUID, List<String>> developerCapabilities,
            List<Task> allTasks) {

        String roleTag = normalizeRole(task.getRoleTag());
        int needed = Math.max(1, task.getStoryPoints());
        Set<UUID> protectedDevs = computeProtectedDevelopers(roleTag, allTasks, developerCapabilities);
        UUID assigned = null;

        // Step 1: Try preferred developers in LLM-ranked order, capability + capacity check
        for (UUID devId : preferredDevs) {
            if (protectedDevs.contains(devId)) continue; // skip — has unmet sole-capability tasks
            List<String> caps = developerCapabilities.getOrDefault(devId, List.of());
            boolean hasCapability = caps.stream().anyMatch(c -> normalizeRole(c).equals(roleTag));
            int cap = remaining.getOrDefault(devId, 0);
            if (hasCapability && cap >= needed) {
                assigned = devId;
                break;
            }
        }

        // Step 2: Try any developer with matching capability and enough capacity
        if (assigned == null) {
            int bestRemaining = Integer.MIN_VALUE;
            for (Map.Entry<UUID, List<String>> entry : developerCapabilities.entrySet()) {
                UUID devId = entry.getKey();
                if (protectedDevs.contains(devId)) continue; // skip — has unmet sole-capability tasks
                boolean hasCapability = entry.getValue().stream()
                    .anyMatch(c -> normalizeRole(c).equals(roleTag));
                int cap = remaining.getOrDefault(devId, 0);
                if (hasCapability && cap >= needed && cap > bestRemaining) {
                    assigned = devId;
                    bestRemaining = cap;
                }
            }
        }

        // Step 2b: Assign over capacity to best available capable developer
        if (assigned == null) {
            int bestRemaining = Integer.MIN_VALUE;
            for (Map.Entry<UUID, List<String>> entry : developerCapabilities.entrySet()) {
                UUID devId = entry.getKey();
                boolean hasCapability = entry.getValue().stream()
                    .anyMatch(c -> normalizeRole(c).equals(roleTag));
                int cap = remaining.getOrDefault(devId, 0);
                if (hasCapability && cap > 0 && cap > bestRemaining) {
                    assigned = devId;
                    bestRemaining = cap;
                }
            }
            if (assigned != null) {
                logger.info("[DISTRIBUTION] Over-capacity assignment: {} task assigned to best available {} developer — PM will see warning",
                    roleTag, roleTag);
            }
        }

        // Step 2c: Sole-capability overflow — allow sole holder to exceed capacity
        // by up to SOLE_CAPABILITY_OVERFLOW_BUFFER_PTS rather than misassigning
        if (assigned == null) {
            boolean isSoleCapability = developerCapabilities.values().stream()
                .filter(caps -> caps.stream()
                    .anyMatch(c -> normalizeRole(c).equals(roleTag)))
                .count() == 1;

            if (isSoleCapability) {
                for (Map.Entry<UUID, List<String>> entry : developerCapabilities.entrySet()) {
                    UUID devId = entry.getKey();
                    boolean hasCapability = entry.getValue().stream()
                        .anyMatch(c -> normalizeRole(c).equals(roleTag));
                    int cap = remaining.getOrDefault(devId, 0);
                    // Allow assignment if over-capacity by no more than the buffer
                    if (hasCapability && cap >= -SOLE_CAPABILITY_OVERFLOW_BUFFER_PTS) {
                        assigned = devId;
                        logger.info("[DISTRIBUTION] Sole-capability overflow: {} task assigned to sole {} " +
                            "developer {}pts over capacity — PM will see warning",
                            roleTag, roleTag, Math.max(0, -cap + needed));
                        break;
                    }
                }
            }
        }

        // Step 3: Cross-capability fallback for small teams
        if (assigned == null) {
            assigned = findCrossCapabilityFallback(roleTag, needed, remaining, developerCapabilities);
        }

        // Step 4: Truly no one can take this task
        if (assigned == null) return null;

        // After determining assigned developer, before returning:
        int afterAssignment = remaining.getOrDefault(assigned, 0) - needed;
        if (afterAssignment < 0) {
            // Mark it but still assign — PM sees this on review screen
            task.setAssignmentReason(
                buildReason(task, assigned, preferredDevs) +
                " ⚠ over capacity by " + Math.abs(afterAssignment) + " pts"
            );
            return assigned;
        }

        return assigned;
    }

    private UUID findCrossCapabilityFallback(
            String roleTag,
            int needed,
            Map<UUID, Integer> remaining,
            Map<UUID, List<String>> developerCapabilities) {

        // No one has the capability — apply cross-role fallback rules
        List<String> fallbackCapabilities = switch (roleTag) {
            case "qa" -> List.of("backend", "frontend", "fullstack");
            case "devops" -> List.of("backend", "fullstack");
            case "android" -> List.of("frontend", "fullstack", "backend");
            case "ios" -> List.of("frontend", "fullstack", "backend");
            default -> List.of();
        };

        for (String fallbackCap : fallbackCapabilities) {
            UUID best = null;
            int bestCap = Integer.MIN_VALUE;
            for (Map.Entry<UUID, List<String>> entry : developerCapabilities.entrySet()) {
                boolean hasFallback = entry.getValue().stream()
                    .anyMatch(c -> normalizeRole(c).equals(fallbackCap));
                int cap = remaining.getOrDefault(entry.getKey(), 0);
                if (hasFallback && cap >= needed && cap > bestCap) {
                    best = entry.getKey();
                    bestCap = cap;
                }
            }
            if (best != null) {
                logger.info("[DISTRIBUTION] Cross-capability fallback: {} task assigned via {} capability",
                    roleTag, fallbackCap);
                return best;
            }
        }

        return null;
    }

    private List<Task> validateAndRebalance(
            List<UserStory> stories,
            Map<UUID, Integer> remaining,
            Map<UUID, List<String>> developerCapabilities) {

        // Check for any developer assigned over capacity (remaining < 0)
        Set<UUID> overCapacityDevs = remaining.entrySet().stream()
            .filter(e -> e.getValue() < 0)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        if (!overCapacityDevs.isEmpty()) {
            for (UUID devId : overCapacityDevs) {
                int overBy = Math.abs(remaining.getOrDefault(devId, 0));
                logger.warn("[DISTRIBUTION] Developer {} is over capacity by {} pts — PM can review on sprint board",
                    devId.toString().substring(0, 8), overBy);
            }
        }

        // Return empty list — no tasks forcibly removed
        return List.of();
    }

    private String buildReason(Task task, UUID assignedDev, List<UUID> preferredDevs) {
        if (!preferredDevs.isEmpty() && preferredDevs.get(0).equals(assignedDev)) {
            return "Top-ranked match for " + task.getRoleTag() + " capability by AI preference";
        }
        return "Assigned by capacity-aware algorithm — best available " + task.getRoleTag() + " developer";
    }

    private String buildUnassignedReason(Task task, Map<UUID, List<String>> developerCapabilities) {
        String roleTag = normalizeRole(task.getRoleTag());
        boolean anyoneHasCap = developerCapabilities.values().stream()
            .anyMatch(caps -> caps.stream().anyMatch(c -> normalizeRole(c).equals(roleTag)));
        if (anyoneHasCap) {
            return "No " + task.getRoleTag() + " developer had remaining capacity — defer or reassign manually";
        }
        return "No developer with " + task.getRoleTag() + " capability on this team — assign manually";
    }

    private Set<UUID> computeProtectedDevelopers(
            String currentRoleTag,
            List<Task> allRemainingTasks,
            Map<UUID, List<String>> developerCapabilities) {

        Set<UUID> protected_ = new HashSet<>();

        // Find capabilities covered by exactly one developer
        Map<String, UUID> soleDeveloperByCapability = new HashMap<>();
        for (Map.Entry<UUID, List<String>> entry : developerCapabilities.entrySet()) {
            for (String cap : entry.getValue()) {
                String normalized = normalizeRole(cap);
                if (soleDeveloperByCapability.containsKey(normalized)) {
                    // More than one developer has this capability — not sole
                    soleDeveloperByCapability.remove(normalized);
                } else {
                    soleDeveloperByCapability.put(normalized, entry.getKey());
                }
            }
        }

        // For each sole-capability developer, check if their sole capability
        // still has unassigned tasks remaining
        for (Map.Entry<String, UUID> entry : soleDeveloperByCapability.entrySet()) {
            String soleCapability = entry.getKey();
            UUID soleDev = entry.getValue();

            // Don't protect if current task IS their sole capability
            if (soleCapability.equals(currentRoleTag)) continue;

            // Check if any unassigned tasks still need this sole capability
            boolean hasUnmetTasks = allRemainingTasks.stream()
                .anyMatch(t -> normalizeRole(t.getRoleTag()).equals(soleCapability)
                    && t.getAssignedDevId() == null);

            if (hasUnmetTasks) {
                protected_.add(soleDev);
            }
        }

        return protected_;
    }

    private int countDevelopersWithCapability(
            String roleTag,
            Map<UUID, List<String>> developerCapabilities) {
        if (developerCapabilities == null) return 0;
        return (int) developerCapabilities.values().stream()
            .filter(caps -> caps.stream()
                .anyMatch(c -> normalizeRole(c).equals(roleTag)))
            .count();
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        return role.trim().toLowerCase(java.util.Locale.ROOT);
    }

    // ── Structured output schema ──────────────────────────────────────────────

    private Schema buildDistributionSchema() {
        Schema itemSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "taskId",           Schema.builder().type(Type.Known.STRING).build(),
                        "preferredDevOrder", Schema.builder()
                                                    .type(Type.Known.ARRAY)
                                                    .items(Schema.builder().type(Type.Known.STRING).build())
                                                    .build()
                ))
                .required(List.of("taskId", "preferredDevOrder"))
                .build();

        return Schema.builder()
                .type(Type.Known.ARRAY)
                .items(itemSchema)
                .build();
    }
}
