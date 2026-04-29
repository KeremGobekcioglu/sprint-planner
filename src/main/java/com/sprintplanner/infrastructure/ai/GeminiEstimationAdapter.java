package com.sprintplanner.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.out.EstimationPort;
import com.sprintplanner.infrastructure.config.GeminiConfig;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
public class GeminiEstimationAdapter implements EstimationPort {

    private static final int[] FIBONACCI = {1, 2, 3, 5, 8, 13};

    private final Client geminiClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public GeminiEstimationAdapter(GeminiConfig geminiConfig, ObjectMapper objectMapper) {
        this.geminiClient = Client.builder().apiKey(geminiConfig.getApiKey()).build();
        this.model = geminiConfig.getModel();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<UserStory> estimate(List<UserStory> userStories, String sprintHistoryMemory) {
        if (userStories == null) return List.of();

        for (UserStory story : userStories) {
            if (story.getTasks() == null) continue;

            String prompt = buildPrompt(story, sprintHistoryMemory);
            String raw = callGemini(prompt);
            applyEstimates(story, raw);
        }

        return userStories;
    }

    // ── Prompt ───────────────────────────────────────────────────────────────

    private String buildPrompt(UserStory story, String sprintHistory) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert agile estimator. Estimate story points for each task using Fibonacci scale.\n\n");
        sb.append("## Allowed values\n1, 2, 3, 5, 8, 13 — no other values are valid.\n\n");

        sb.append("## User Story\n").append(story.getTitle()).append("\n\n");

        sb.append("## Tasks to estimate\n");
        for (Task task : story.getTasks()) {
            sb.append("- id: ").append(task.getId()).append("\n");
            sb.append("  title: ").append(task.getTitle()).append("\n");
            sb.append("  description: ").append(task.getDescription()).append("\n");
            sb.append("  roleTag: ").append(task.getRoleTag()).append("\n");
            sb.append("  acceptanceCriterion: ").append(task.getAcceptanceCriterion()).append("\n\n");
        }

        if (sprintHistory != null && !sprintHistory.isBlank()) {
            sb.append("## Sprint History (use to self-correct known estimation biases)\n");
            sb.append(sprintHistory).append("\n\n");
        }

        sb.append("## Estimation Guidelines\n");
        sb.append("- backend tasks: typically 3–8 pts depending on complexity\n");
        sb.append("- frontend tasks: typically 2–5 pts\n");
        sb.append("- qa tasks: typically 1–3 pts\n");
        sb.append("- devops tasks: typically 2–5 pts\n");
        sb.append("- Tasks with 8 or 13 pts should be rare — only for genuinely complex work\n\n");

        sb.append("## Output Rules\n");
        sb.append("Respond ONLY with a valid JSON array. No explanation, no markdown, no backticks.\n");
        sb.append("Each element: { \"id\": \"<task-uuid>\", \"points\": <fibonacci-number> }\n\n");

        sb.append("## Example Output\n");
        sb.append("""
            [
              { "id": "123e4567-e89b-12d3-a456-426614174000", "points": 5 },
              { "id": "223e4567-e89b-12d3-a456-426614174001", "points": 3 },
              { "id": "323e4567-e89b-12d3-a456-426614174002", "points": 2 }
            ]
            """);

        return sb.toString();
    }

    // ── Gemini call ──────────────────────────────────────────────────────────

    private String callGemini(String prompt) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(0.2f) // lower temp for estimation — we want consistency
                .build();

        GenerateContentResponse response = geminiClient.models.generateContent(
                model, prompt, config
        );

        return response.text();
    }

    // ── Apply estimates back to tasks ────────────────────────────────────────

    private void applyEstimates(UserStory story, String raw) {
        try {
            String cleaned = raw.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isArray()) return;

            // Build a quick lookup: taskId -> points
            java.util.Map<String, Integer> pointsById = new java.util.HashMap<>();
            for (JsonNode node : root) {
                String id = node.path("id").asText();
                int points = toNearestFibonacci(node.path("points").asInt(2));
                pointsById.put(id, points);
            }

            int storyTotal = 0;
            for (Task task : story.getTasks()) {
                int points = pointsById.getOrDefault(task.getId().toString(), 2);
                task.setStoryPoints(points);
                storyTotal += points;
            }

            story.setStoryPoints(toNearestFibonacci(storyTotal));

        } catch (Exception e) {
            // Parsing failed — fall back to role-based defaults
            applyFallbackEstimates(story);
        }
    }

    private void applyFallbackEstimates(UserStory story) {
        int total = 0;
        for (Task task : story.getTasks()) {
            int points = switch (task.getRoleTag() == null ? "" : task.getRoleTag().toLowerCase()) {
                case "backend" -> 5;
                case "frontend" -> 3;
                case "qa" -> 2;
                case "devops" -> 3;
                default -> 2;
            };
            task.setStoryPoints(points);
            total += points;
        }
        story.setStoryPoints(toNearestFibonacci(total));
    }

    private int toNearestFibonacci(int value) {
        int best = FIBONACCI[0];
        int bestDistance = Math.abs(value - best);
        for (int f : FIBONACCI) {
            int distance = Math.abs(value - f);
            if (distance < bestDistance) {
                best = f;
                bestDistance = distance;
            }
        }
        return best;
    }
}