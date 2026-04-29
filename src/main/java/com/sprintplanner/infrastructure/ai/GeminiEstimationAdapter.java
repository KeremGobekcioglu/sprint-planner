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
import java.util.Map;

@Component
@Primary
public class GeminiEstimationAdapter implements EstimationPort {

    private static final int[] FIBONACCI = {1, 2, 3, 5, 8, 13};

    private final GeminiClientProvider gemini;
    private final ObjectMapper objectMapper;

    public GeminiEstimationAdapter(GeminiClientProvider gemini, ObjectMapper objectMapper) {
        this.gemini = gemini;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<UserStory> estimate(List<UserStory> userStories, String sprintHistoryMemory) {
        if (userStories == null || userStories.isEmpty()) return List.of();

        // ONE call for all stories instead of one per story
        String prompt = buildBatchPrompt(userStories, sprintHistoryMemory);
        String raw = gemini.generate(prompt, 0.2f, GeminiClientProvider.MODEL_ESTIMATION);
        applyBatchEstimates(userStories, raw);

        return userStories;
    }

    // ── Prompt ───────────────────────────────────────────────────────────────

    private String buildBatchPrompt(List<UserStory> stories, String sprintHistory) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert agile estimator. Estimate story points for ALL tasks below using Fibonacci scale.\n\n");
        sb.append("## Allowed values\n1, 2, 3, 5, 8, 13 — no other values are valid.\n\n");

        sb.append("## All Tasks to Estimate\n");
        for (UserStory story : stories) {
            if (story.getTasks() == null) continue;
            sb.append("Story: ").append(story.getTitle()).append("\n");
            for (Task task : story.getTasks()) {
                sb.append("  - id: ").append(task.getId()).append("\n");
                sb.append("    title: ").append(task.getTitle()).append("\n");
                sb.append("    roleTag: ").append(task.getRoleTag()).append("\n");
                sb.append("    description: ").append(task.getDescription()).append("\n\n");
            }
        }

        if (sprintHistory != null && !sprintHistory.isBlank()) {
            sb.append("## Sprint History\n").append(sprintHistory).append("\n\n");
        }

        sb.append("## Guidelines\n");
        sb.append("- backend: 3–8 pts, frontend: 2–5 pts, qa: 1–3 pts, devops: 2–5 pts\n\n");

        sb.append("## Output Rules\n");
        sb.append("Respond ONLY with a raw JSON array. No explanation, no markdown, no backticks.\n");
        sb.append("Each element: { \"id\": \"<task-uuid>\", \"points\": <fibonacci-number> }\n");

        return sb.toString();
    }




    // ── Apply estimates back to tasks ────────────────────────────────────────

    private void applyBatchEstimates(List<UserStory> stories, String raw) {
        try {
            String cleaned = raw.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isArray()) {
                stories.forEach(this::applyFallbackEstimates);
                return;
            }

            Map<String, Integer> pointsById = new java.util.HashMap<>();
            for (JsonNode node : root) {
                pointsById.put(
                        node.path("id").asText(),
                        toNearestFibonacci(node.path("points").asInt(2))
                );
            }

            for (UserStory story : stories) {
                if (story.getTasks() == null) continue;
                int storyTotal = 0;
                for (Task task : story.getTasks()) {
                    int points = pointsById.getOrDefault(task.getId().toString(), 2);
                    task.setStoryPoints(points);
                    storyTotal += points;
                }
                story.setStoryPoints(toNearestFibonacci(storyTotal));
            }

        } catch (Exception e) {
            stories.forEach(this::applyFallbackEstimates);
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