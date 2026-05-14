package com.sprintplanner.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.dto.SprintPlanMapper;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.out.DistributionPort;
import com.sprintplanner.infrastructure.config.GeminiConfig;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Primary
public class GeminiDistributionAdapter implements DistributionPort {

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
            String teamProfileMemory,
            String sprintHistoryMemory
    ) {
        if (estimatedUserStories == null || estimatedUserStories.isEmpty()) {
            return new SprintPlan(teamId, sprintGoal, List.of(), List.of());
        }
        if (capacityByDeveloper == null || capacityByDeveloper.isEmpty()) {
            return convertToSprintPlan(teamId, sprintGoal, estimatedUserStories, List.of());
        }

        String prompt = buildPrompt(
                estimatedUserStories, capacityByDeveloper, developerCapabilities,
                teamProfileMemory, sprintHistoryMemory
        );

        String raw = gemini.generate(prompt, 0.3f, GeminiClientProvider.MODEL_DISTRIBUTION);

        List<Task> unassignedTasks = applyAssignments(
            estimatedUserStories,
            capacityByDeveloper,
            developerCapabilities,
            raw
        );

        return convertToSprintPlan(teamId, sprintGoal, estimatedUserStories, unassignedTasks);
    }

    private SprintPlan convertToSprintPlan(UUID teamId, String sprintGoal, List<UserStory> userStories, List<Task> unassignedTasks) {
        return new SprintPlan(
            teamId,
            sprintGoal,
            userStories.stream()
                .map(SprintPlanMapper::toDto)
                .toList(),
            unassignedTasks == null
                ? List.of()
                : unassignedTasks.stream().map(SprintPlanMapper::toDto).toList()
        );
    }

    // ── Prompt ───────────────────────────────────────────────────────────────

    private String buildPrompt(
            List<UserStory> stories,
            Map<UUID, Integer> capacityByDeveloper,
            Map<UUID, List<String>> developerCapabilities,
            String teamProfile,
            String sprintHistory
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert sprint planner. Assign each task to a developer.\n\n");

        sb.append("## Developers and their capacity (story points available)\n");
        for (Map.Entry<UUID, Integer> entry : capacityByDeveloper.entrySet()) {
            List<String> capabilities = developerCapabilities == null
                ? List.of()
                : developerCapabilities.getOrDefault(entry.getKey(), List.of());
            sb.append("- developerId: ").append(entry.getKey())
                .append(" | capabilities: ").append(capabilities.isEmpty() ? "none" : String.join(", ", capabilities))
                .append(" | capacity: ").append(entry.getValue()).append(" pts\n");
        }
        sb.append("\n");

        sb.append("## Tasks to assign\n");
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
            sb.append("## Team Preferences (follow these for assignments)\n")
                    .append(teamProfile).append("\n\n");
        }

        if (sprintHistory != null && !sprintHistory.isBlank()) {
            sb.append("## Sprint History (use per-developer performance by task type)\n")
                    .append(sprintHistory).append("\n\n");
        }

        sb.append("## Assignment Rules\n");
        sb.append("- Track remaining capacity per developer as you assign tasks.\n");
        sb.append("- Never exceed a developer's remaining capacity.\n");
        sb.append("- Only assign a task to a developer whose capabilities include the task roleTag.\n");
        sb.append("- If no developer has the matching roleTag, return: { \"taskId\": \"...\", \"unassigned\": true }.\n");
        sb.append("- Distribute load evenly among developers with the same capability.\n");
        sb.append("- If all role-matched developers are full, assign to the best remaining fit and set overCapacity: true.\n\n");

        sb.append("## Output Rules\n");
        sb.append("Respond ONLY with a valid JSON array. No explanation, no markdown, no backticks.\n");
        sb.append("Each element is either:\n");
        sb.append("- { \"taskId\": \"<uuid>\", \"developerId\": \"<uuid>\", \"overCapacity\": <true|false> }\n");
        sb.append("- { \"taskId\": \"<uuid>\", \"unassigned\": true }\n\n");

        sb.append("## Example Output\n");
        sb.append("""
            [
              { "taskId": "aaa-111", "developerId": "dev-001", "overCapacity": false },
              { "taskId": "bbb-222", "unassigned": true },
              { "taskId": "ccc-333", "developerId": "dev-001", "overCapacity": true }
            ]
            """);

        return sb.toString();
    }

    // ── Apply assignments back to tasks ──────────────────────────────────────

    private List<Task> applyAssignments(
            List<UserStory> stories,
            Map<UUID, Integer> capacityByDeveloper,
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

        Map<UUID, Integer> remaining = new LinkedHashMap<>(capacityByDeveloper);
        List<Task> unassignedTasks = new ArrayList<>();

        try {
            String cleaned = raw.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isArray()) {
                return applyFallbackAssignments(stories, remaining, developerCapabilities);
            }

            Set<String> validDevIds = new HashSet<>();
            for (UUID id : capacityByDeveloper.keySet()) {
                validDevIds.add(id.toString());
            }

            for (JsonNode node : root) {
                String taskId = node.path("taskId").asText();
                boolean unassigned = node.path("unassigned").asBoolean(false);

                Task task = taskById.get(taskId);
                if (task == null) continue;

                if (unassigned) {
                    task.setAssignedDevId(null);
                    unassignedTasks.add(task);
                    continue;
                }

                String devId = node.path("developerId").asText();
                if (!validDevIds.contains(devId)) {
                    continue;
                }

                UUID developerId = UUID.fromString(devId);
                AssignmentResult result = assignTaskWithGuards(
                    task,
                    developerId,
                    remaining,
                    developerCapabilities
                );
                if (result.unassigned) {
                    unassignedTasks.add(task);
                }
            }

        } catch (Exception e) {
            return applyFallbackAssignments(stories, remaining, developerCapabilities);
        }

        return unassignedTasks;
    }

    private List<Task> applyFallbackAssignments(
            List<UserStory> stories,
            Map<UUID, Integer> remaining,
            Map<UUID, List<String>> developerCapabilities
    ) {
        List<Task> unassignedTasks = new ArrayList<>();

        for (UserStory story : stories) {
            if (story.getTasks() == null) continue;
            for (Task task : story.getTasks()) {
                AssignmentResult result = assignTaskWithGuards(
                    task,
                    null,
                    remaining,
                    developerCapabilities
                );
                if (result.unassigned) {
                    unassignedTasks.add(task);
                }
            }
        }

        return unassignedTasks;
    }

    private AssignmentResult assignTaskWithGuards(
        Task task,
        UUID preferredDeveloper,
        Map<UUID, Integer> remaining,
        Map<UUID, List<String>> developerCapabilities
    ) {
        String roleTag = normalizeRole(task.getRoleTag());
        Map<String, List<UUID>> roleToDevelopers = buildRoleMap(developerCapabilities);
        List<UUID> roleDevelopers = roleToDevelopers.getOrDefault(roleTag, List.of());

        if (roleDevelopers.isEmpty()) {
            task.setAssignedDevId(null);
            return AssignmentResult.unassigned();
        }

        UUID selected = selectRoleDeveloper(roleDevelopers, preferredDeveloper, remaining, task.getStoryPoints());
        if (selected == null) {
            task.setAssignedDevId(null);
            return AssignmentResult.unassigned();
        }

        task.setAssignedDevId(selected);
        remaining.merge(selected, -Math.max(1, task.getStoryPoints()), Integer::sum);
        return AssignmentResult.assigned();
    }

    private UUID selectRoleDeveloper(
        List<UUID> roleDevelopers,
        UUID preferredDeveloper,
        Map<UUID, Integer> remaining,
        int storyPoints
    ) {
        int needed = Math.max(1, storyPoints);
        UUID preferred = preferredDeveloper != null && roleDevelopers.contains(preferredDeveloper)
            ? preferredDeveloper
            : null;

        if (preferred != null && remaining.getOrDefault(preferred, 0) >= needed) {
            return preferred;
        }

        UUID bestFit = null;
        int bestRemaining = Integer.MIN_VALUE;
        for (UUID developerId : roleDevelopers) {
            int capacity = remaining.getOrDefault(developerId, 0);
            if (capacity >= needed && capacity > bestRemaining) {
                bestFit = developerId;
                bestRemaining = capacity;
            }
        }

        if (bestFit != null) {
            return bestFit;
        }

        UUID fallback = null;
        int fallbackRemaining = Integer.MIN_VALUE;
        for (UUID developerId : roleDevelopers) {
            int capacity = remaining.getOrDefault(developerId, 0);
            if (capacity > fallbackRemaining) {
                fallbackRemaining = capacity;
                fallback = developerId;
            }
        }

        return fallback;
    }

    private Map<String, List<UUID>> buildRoleMap(Map<UUID, List<String>> developerCapabilities) {
        Map<String, List<UUID>> roleToDevelopers = new HashMap<>();
        if (developerCapabilities == null) {
            return roleToDevelopers;
        }
        for (Map.Entry<UUID, List<String>> entry : developerCapabilities.entrySet()) {
            List<String> capabilities = entry.getValue() == null ? List.of() : entry.getValue();
            for (String capability : capabilities) {
                String normalized = normalizeRole(capability);
                if (normalized.isBlank()) {
                    continue;
                }
                roleToDevelopers.computeIfAbsent(normalized, key -> new ArrayList<>()).add(entry.getKey());
            }
        }
        return roleToDevelopers;
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        return role.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static final class AssignmentResult {
        private final boolean unassigned;

        private AssignmentResult(boolean unassigned) {
            this.unassigned = unassigned;
        }

        private static AssignmentResult unassigned() {
            return new AssignmentResult(true);
        }

        private static AssignmentResult assigned() {
            return new AssignmentResult(false);
        }
    }
}
