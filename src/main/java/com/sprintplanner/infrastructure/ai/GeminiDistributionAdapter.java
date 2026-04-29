package com.sprintplanner.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.sprintplanner.domain.dto.SprintPlan;
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

    private final Client geminiClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public GeminiDistributionAdapter(GeminiConfig geminiConfig, ObjectMapper objectMapper) {
        this.geminiClient = Client.builder().apiKey(geminiConfig.getApiKey()).build();
        this.model = geminiConfig.getModel();
        this.objectMapper = objectMapper;
    }

    @Override
    public SprintPlan distribute(
            UUID teamId,
            String sprintGoal,
            List<UserStory> estimatedUserStories,
            Map<UUID, Integer> capacityByDeveloper,
            String teamProfileMemory,
            String sprintHistoryMemory
    ) {
        if (estimatedUserStories == null || estimatedUserStories.isEmpty()) {
            return new SprintPlan(teamId, sprintGoal, List.of());
        }
        if (capacityByDeveloper == null || capacityByDeveloper.isEmpty()) {
            return new SprintPlan(teamId, sprintGoal, estimatedUserStories);
        }

        String prompt = buildPrompt(
                estimatedUserStories, capacityByDeveloper,
                teamProfileMemory, sprintHistoryMemory
        );

        String raw = callGemini(prompt);
        applyAssignments(estimatedUserStories, capacityByDeveloper, raw);

        return new SprintPlan(teamId, sprintGoal, estimatedUserStories);
    }

    // ── Prompt ───────────────────────────────────────────────────────────────

    private String buildPrompt(
            List<UserStory> stories,
            Map<UUID, Integer> capacityByDeveloper,
            String teamProfile,
            String sprintHistory
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert sprint planner. Assign each task to a developer.\n\n");

        sb.append("## Developers and their capacity (story points available)\n");
        for (Map.Entry<UUID, Integer> entry : capacityByDeveloper.entrySet()) {
            sb.append("- developerId: ").append(entry.getKey())
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
        sb.append("- Match task roleTag to developer's role where possible.\n");
        sb.append("- Do NOT assign more points to a developer than their capacity.\n");
        sb.append("- Distribute load evenly — avoid overloading one developer.\n");
        sb.append("- If a task cannot be assigned without exceeding all capacities, still assign it to the best fit and flag it.\n\n");

        sb.append("## Output Rules\n");
        sb.append("Respond ONLY with a valid JSON array. No explanation, no markdown, no backticks.\n");
        sb.append("Each element: { \"taskId\": \"<uuid>\", \"developerId\": \"<uuid>\", \"overCapacity\": <true|false> }\n\n");

        sb.append("## Example Output\n");
        sb.append("""
            [
              { "taskId": "aaa-111", "developerId": "dev-001", "overCapacity": false },
              { "taskId": "bbb-222", "developerId": "dev-002", "overCapacity": false },
              { "taskId": "ccc-333", "developerId": "dev-001", "overCapacity": true }
            ]
            """);

        return sb.toString();
    }

    // ── Gemini call ──────────────────────────────────────────────────────────

    private String callGemini(String prompt) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(0.3f)
                .build();

        GenerateContentResponse response = geminiClient.models.generateContent(
                model, prompt, config
        );

        return response.text();
    }

    // ── Apply assignments back to tasks ──────────────────────────────────────

    private void applyAssignments(
            List<UserStory> stories,
            Map<UUID, Integer> capacityByDeveloper,
            String raw
    ) {
        // Build taskId -> task lookup
        Map<String, Task> taskById = new HashMap<>();
        for (UserStory story : stories) {
            if (story.getTasks() == null) continue;
            for (Task task : story.getTasks()) {
                taskById.put(task.getId().toString(), task);
            }
        }

        try {
            String cleaned = raw.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isArray()) {
                applyFallbackAssignments(stories, capacityByDeveloper);
                return;
            }

            // Validate developer IDs returned by LLM are real
            Set<String> validDevIds = new HashSet<>();
            for (UUID id : capacityByDeveloper.keySet()) {
                validDevIds.add(id.toString());
            }

            for (JsonNode node : root) {
                String taskId = node.path("taskId").asText();
                String devId = node.path("developerId").asText();

                Task task = taskById.get(taskId);
                if (task == null) continue;                      // unknown task — skip
                if (!validDevIds.contains(devId)) continue;      // hallucinated dev — skip

                task.setAssignedDevId(UUID.fromString(devId));
            }

        } catch (Exception e) {
            applyFallbackAssignments(stories, capacityByDeveloper);
        }
    }

    // Fallback: round-robin by capacity if LLM parse fails
    private void applyFallbackAssignments(
            List<UserStory> stories,
            Map<UUID, Integer> capacityByDeveloper
    ) {
        List<UUID> devs = new ArrayList<>(capacityByDeveloper.keySet());
        if (devs.isEmpty()) return;

        Map<UUID, Integer> remaining = new LinkedHashMap<>(capacityByDeveloper);
        int pointer = 0;

        for (UserStory story : stories) {
            if (story.getTasks() == null) continue;
            for (Task task : story.getTasks()) {
                UUID assigned = devs.get(pointer % devs.size());
                task.setAssignedDevId(assigned);
                remaining.merge(assigned, -task.getStoryPoints(), Integer::sum);
                pointer++;
            }
        }
    }
}
