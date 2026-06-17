package com.sprintplanner.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.out.EstimationPort;
import com.sprintplanner.infrastructure.config.GeminiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Primary
public class GeminiEstimationAdapter implements EstimationPort {

    private static final Logger logger = LoggerFactory.getLogger(GeminiEstimationAdapter.class);
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

        String prompt = buildBatchPrompt(userStories, sprintHistoryMemory);
        Schema schema = buildEstimationSchema();

        // Fix 3: single retry before falling back to heuristics
        boolean applied = tryApplyBatchEstimates(userStories,
                gemini.generate(prompt, 0.2f, GeminiClientProvider.MODEL_ESTIMATION, schema));

        if (!applied) {
            logger.warn("[ESTIMATION] First attempt failed to parse, retrying with same prompt...");
            boolean retryApplied = tryApplyBatchEstimates(userStories,
                    gemini.generate(prompt, 0.2f, GeminiClientProvider.MODEL_ESTIMATION, schema));
            if (!retryApplied) {
                logger.warn("[ESTIMATION] Retry also failed, applying role-based fallback estimates");
                userStories.forEach(this::applyFallbackEstimates);
            }
        }

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

        sb.append("## Estimation Approach\n");
        sb.append("First, classify each task by size. Then convert to points.\n\n");

        sb.append("Size classification:\n");
        sb.append("XS — trivial: config change, add a field, fix a label, add logging → 1pt\n");
        sb.append("S  — simple: new endpoint following existing pattern, basic UI component, unit tests → 2pts\n");
        sb.append("M  — moderate: new feature with some coordination, simple SDK integration, scheduled job → 3pts\n");
        sb.append("L  — complex: multi-endpoint feature, complex UI, external payment/search integration → 5pts\n");
        sb.append("XL — very complex: reconciliation algorithm, real-time sync, data migration → 8pts\n\n");

        sb.append("Classification rules:\n");
        sb.append("- Read the task title and description carefully before classifying\n");
        sb.append("- If the epic or task name contains 'Lite', 'Basic', 'Simple', 'MVP', 'Health Check': classify as XS or S\n");
        sb.append("- If codebase context shows this functionality already partially exists: go one size smaller\n");
        sb.append("- A mobile native screen (iOS/Android) is typically M or L, not XL\n");
        sb.append("- Config, setup, and deploy tasks are XS or S unless they involve new infrastructure\n");
        sb.append("- Most tasks in a well-scoped sprint should be S or M — not everything is L or XL\n\n");

        // Fix 3: variety-check lines removed — each task is estimated independently on its own merits

        sb.append("## Output Rules\n");
        sb.append("Respond ONLY with a raw JSON array. No explanation outside the array, no markdown, no backticks.\n");
        sb.append("For each task, first classify the size, then assign points.\n");
        sb.append("Each element: { \"id\": \"<task-uuid>\", \"size\": \"<XS|S|M|L|XL>\", \"reasoning\": \"<one sentence why>\", \"points\": <fibonacci-number> }\n\n");
        return sb.toString();
    }


    // ── Apply estimates back to tasks ────────────────────────────────────────

    /**
     * Fix 3: returns true on successful parse+apply, false on any parse failure.
     * The caller owns the retry/fallback decision.
     */
    private boolean tryApplyBatchEstimates(List<UserStory> stories, String raw) {
        try {
            String cleaned = raw.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isArray()) {
                return false;
            }

            Map<String, Integer> pointsById = new java.util.HashMap<>();
            for (JsonNode node : root) {
                String taskId = node.path("id").asText();
                pointsById.put(taskId, toNearestFibonacci(node.path("points").asInt(2)));
                String size = node.path("size").asText("?");
                String reasoning = node.path("reasoning").asText("");
                logger.debug("[ESTIMATION] Task {}: {} ({}) → {}pts",
                        taskId.length() > 8 ? taskId.substring(0, 8) : taskId,
                        size, reasoning,
                        pointsById.get(taskId));
            }

            for (UserStory story : stories) {
                if (story.getTasks() == null) continue;
                int storyTotal = 0;
                for (Task task : story.getTasks()) {
                    String taskIdStr = task.getId().toString();
                    if (!pointsById.containsKey(taskIdStr)) {
                        // Fix 3: warn explicitly rather than silently defaulting
                        logger.warn("[ESTIMATION] Task ID {} ('{}') not found in model response — applying default of 2pts",
                                taskIdStr, task.getTitle());
                    }
                    int points = pointsById.getOrDefault(taskIdStr, 2);
                    task.setStoryPoints(points);
                    storyTotal += points;
                }
                story.setStoryPoints(toNearestFibonacci(storyTotal));
            }
            return true;

        } catch (Exception e) {
            logger.warn("[ESTIMATION] Failed to parse model response: {}", e.getMessage());
            return false;
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

    // ── Structured output schema ──────────────────────────────────────────────

    private Schema buildEstimationSchema() {
        Schema itemSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "id",        Schema.builder().type(Type.Known.STRING).build(),
                        "size",      Schema.builder().type(Type.Known.STRING)
                                             .enum_(List.of("XS", "S", "M", "L", "XL"))
                                             .build(),
                        "reasoning", Schema.builder().type(Type.Known.STRING).build(),
                        "points",    Schema.builder().type(Type.Known.INTEGER).build()
                ))
                .required(List.of("id", "size", "reasoning", "points"))
                .build();

        return Schema.builder()
                .type(Type.Known.ARRAY)
                .items(itemSchema)
                .build();
    }
}
