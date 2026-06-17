package com.sprintplanner.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.sprintplanner.domain.dto.CarryoverTaskDto;
import com.sprintplanner.domain.dto.Epic;
import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.out.BreakdownPort;
import com.sprintplanner.infrastructure.config.GeminiConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Primary
public class GeminiBreakdownAdapter implements BreakdownPort {

    private static final Logger logger = LoggerFactory.getLogger(GeminiBreakdownAdapter.class);

    private final GeminiClientProvider gemini;
    private final ObjectMapper objectMapper;

    public GeminiBreakdownAdapter(GeminiClientProvider gemini, ObjectMapper objectMapper) {
        this.gemini = gemini;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<UserStory> breakdown(
            PlanningRequest request,
            String teamProfileMemory,
            String sprintHistoryMemory,
            int totalCapacity,
            Map<String, Integer> capacityByCapability,
            Set<String> uncoveredCapabilities,
            String codebaseContext,
            String developerSummary
    ) {
        if (teamProfileMemory != null && !teamProfileMemory.isBlank()) {
            logger.info("[BREAKDOWN] Team profile memory loaded ({} chars): ...{}",
                teamProfileMemory.length(),
                teamProfileMemory.substring(0, Math.min(200, teamProfileMemory.length())));
        } else {
            logger.info("[BREAKDOWN] No team profile memory — fresh start mode");
        }

        if (sprintHistoryMemory != null && !sprintHistoryMemory.isBlank()) {
            logger.info("[BREAKDOWN] Sprint history memory loaded ({} chars)", sprintHistoryMemory.length());
        } else {
            logger.info("[BREAKDOWN] No sprint history memory");
        }
        List<Epic> epics = request.epics() == null ? List.of() : request.epics();
        if (epics.isEmpty()) return List.of();

        Schema schema = buildBreakdownSchema();

        // ONE call for all epics
        String prompt = buildBatchPrompt(epics, request, teamProfileMemory, sprintHistoryMemory, totalCapacity, capacityByCapability, uncoveredCapabilities, codebaseContext, developerSummary);
        String raw = gemini.generate(prompt, 0.4f, GeminiClientProvider.MODEL_BREAKDOWN, schema);
        List<UserStory> stories = parseResponse(raw, epics);

        // Retry once if validation fails — resend full original prompt with a schema-violation prefix
        if (!isValid(stories)) {
            String retryPrompt = buildRetryPrompt(prompt, raw);
            String retryRaw = gemini.generate(retryPrompt, 0.4f, GeminiClientProvider.MODEL_BREAKDOWN_RETRY, schema);
            stories = parseResponse(retryRaw, epics);
        }

        return stories;
    }


    private String buildBatchPrompt(List<Epic> epics, PlanningRequest request,
                                    String teamProfile, String sprintHistory, int totalCapacity,
                                    Map<String, Integer> capacityByCapability, Set<String> uncoveredCapabilities,
                                    String codebaseContext, String developerSummary) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert agile sprint planner. Break down ALL epics below into user stories and tasks.\n\n");

        sb.append("## Domain\n").append(request.domain()).append("\n\n");

        if (developerSummary != null && !developerSummary.isBlank()) {
            sb.append("## Team Composition\n");
            sb.append("These are the actual developers on this team and what they can work on:\n");
            sb.append(developerSummary);
            sb.append("\n");
            sb.append("IMPORTANT: Each line above is ONE person. If one developer has multiple capabilities (e.g. ios, android, frontend),\n");
            sb.append("do not create separate tasks for each capability — create ONE task for that person covering all relevant platforms.\n");
            sb.append("Match task roleTags to what developers actually cover. Do not generate tasks for capabilities not listed above.\n\n");
        }

        sb.append("## Sprint Goal\n").append(request.sprintGoal()).append("\n\n");

        sb.append("## Epics\n");
        for (int i = 0; i < epics.size(); i++) {
            Epic epic = epics.get(i);
            sb.append((i + 1)).append(". Title: ").append(epic.title()).append("\n");
            if (epic.description() != null && !epic.description().isBlank()) {
                sb.append("   Description: ").append(epic.description()).append("\n");
            }
        }
        sb.append("\n");

        if (teamProfile != null && !teamProfile.isBlank()) {
            sb.append("## Team Preferences\n").append(teamProfile).append("\n\n");
        }

        if (sprintHistory != null && !sprintHistory.isBlank()) {
            sb.append("## Sprint History\n").append(sprintHistory).append("\n\n");
        }

        sb.append("## Domain-Specific Rules\n");
        switch (request.domain()) {
            case "FINTECH" -> sb.append(
                    "- Strict testable acceptance criteria.\n" +
                            "- Include error handling and compliance tasks.\n" +
                            "- Conservative estimates.\n"
            );
            case "E_COMMERCE" -> sb.append(
                    "- Include A/B test and analytics tasks.\n" +
                            "- Mobile-first UI tasks must be explicit.\n"
            );
            default -> sb.append(
                    "- Balanced breakdown, flag ambiguous requirements.\n"
            );
        }
        sb.append("\n");

        sb.append("## Capacity Budget by Role\n");
        // Fix 6: replaced contradictory capacity block with non-contradictory version
        sb.append("Generate exactly 1 story per epic covering its core requirement. Every epic must be covered.\n");
        sb.append("Use the role budgets below as sizing guidance only — over-budget tasks are surfaced to the PM, not dropped.\n");
        sb.append("Generate only tasks each epic genuinely needs. Do not invent supporting tasks.\n");
        if (capacityByCapability != null && !capacityByCapability.isEmpty()) {
            for (Map.Entry<String, Integer> entry : capacityByCapability.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" pts available\n");
            }
        } else {
            sb.append("Total team capacity for this sprint: ").append(totalCapacity).append(" story points.\n");
            sb.append("Assume backend tasks ~5pts, frontend ~3pts, qa ~2pts, devops ~3pts.\n");
        }
        if (uncoveredCapabilities != null && !uncoveredCapabilities.isEmpty()) {
            sb.append("\n## Roles with NO developer coverage\n");
            sb.append("Do NOT generate tasks with these roleTags — no developer can handle them:\n");
            for (String cap : uncoveredCapabilities) {
                sb.append("- ").append(cap).append("\n");
            }
        }
        sb.append("\n");

        List<CarryoverTaskDto> carryovers = request.carryoverTasks();
        if (carryovers != null && !carryovers.isEmpty()) {
            sb.append("## Carry-over Note\n");
            sb.append("Note: ").append(carryovers.size())
                .append(" carry-over task(s) from the previous sprint are included separately. ")
                .append("Do not regenerate tasks for topics that appear to be carry-over work.\n\n");
        }

        if (codebaseContext != null && !codebaseContext.isBlank()) {
            sb.append("## Codebase Context\n");
            sb.append(codebaseContext).append("\n\n");
            sb.append("Use this context to:\n");
            sb.append("- Avoid generating tasks for functionality that already exists\n");
            sb.append("- Match task complexity to the existing architecture\n");
            sb.append("- Use correct technology names in task titles and descriptions\n");
            sb.append("- Adjust task scope based on existing infrastructure\n\n");
        }
        sb.append("## Output Rules\n");
        sb.append("Respond ONLY with a raw JSON array. No explanation, no markdown, no backticks.\n");
        sb.append("Each element is a user story object with: epicTitle (string), title (string), tasks (array of objects).\n");
        sb.append("Each task must have: title, description, acceptanceCriterion, roleTag.\n\n");

        sb.append("ROLE TAGS: Use only roles that appear in the Capacity Budget by Role section above.\n");
        sb.append("  Do not use a roleTag that has 0pts or no developer coverage.\n");
        sb.append("  If no QA developer exists, fold testing into the implementation task description and acceptance criteria.\n");
        sb.append("  If a developer has both backend and frontend capabilities, you may assign them a full-stack task.\n\n");

        sb.append("STORY COUNT: Generate exactly 1 story for EVERY epic in the list above. No epic may be skipped.\n");
        sb.append("  Generate 2 stories for an epic only if it has clearly distinct user-facing and system-level parts.\n\n");

        sb.append("TASK COUNT: Match the number of tasks to the number of distinct role types needed.\n");
        sb.append("  If a feature needs backend + frontend: 2 tasks.\n");
        sb.append("  If a feature needs backend + frontend + devops: 3 tasks.\n");
        sb.append("  If a feature needs only backend: 1 task.\n");
        sb.append("  Do not create a task just to fill a slot.\n\n");

        sb.append("PLATFORM TASKS: Base platform task count on the team's actual platform developers.\n");
        sb.append("  If the team has separate Android and iOS developers: create one task per platform.\n");
        sb.append("  If one developer covers both Android and iOS: create one combined mobile task.\n");
        sb.append("  If the team has no mobile developers: do not generate android or ios tasks.\n\n");

        sb.append("COMBINING TASKS: Fold simple supporting work (logging, config, basic analytics) into the\n");
        sb.append("  main implementation task description rather than creating separate tasks.\n\n");
        sb.append("## Example Output Shape\n");
        sb.append("""
        [
          {
            "epicTitle": "Add IBAN validation to money transfer flow",
            "title": "User can complete IBAN transfer",
            "tasks": [
              {
                "title": "Implement IBAN validation endpoint",
                "description": "POST /transfers/validate-iban",
                "acceptanceCriterion": "Invalid IBANs return 400. Valid IBANs return 200.",
                "roleTag": "backend"
              },
              {
                "title": "Build Android transfer screen",
                "description": "Native Android screen for IBAN entry and transfer confirmation.",
                "acceptanceCriterion": "User can submit a transfer on Android. Error states shown inline.",
                "roleTag": "android"
              }
            ]
          }
        ]
        """);

        return sb.toString();
    }


    // ── Parsing ──────────────────────────────────────────────────────────────

    private List<UserStory> parseResponse(String raw, List<Epic> epics) {
        List<UserStory> stories = new ArrayList<>();

        // Fix 5: LinkedHashSet preserves input order for deterministic fallback epic title
        Set<String> epicTitles = epics.stream()
                .map(Epic::title)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        try {
            String cleaned = raw.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isArray()) return stories;

            for (JsonNode storyNode : root) {
                String epicTitle = storyNode.path("epicTitle").asText("");
                if (epicTitle.isBlank() && !epicTitles.isEmpty()) {
                    // First epic in input order (LinkedHashSet guarantees this)
                    epicTitle = epicTitles.iterator().next();
                }

                UserStory story = new UserStory(
                        UUID.randomUUID(),
                        null,
                        epicTitle,
                        storyNode.path("title").asText("Untitled Story"),
                        0,
                        "PLANNED"
                );

                List<Task> tasks = new ArrayList<>();
                for (JsonNode taskNode : storyNode.path("tasks")) {
                    Task task = new Task(
                            UUID.randomUUID(),
                            story.getId(),
                            null,
                            taskNode.path("title").asText("Untitled Task"),
                            taskNode.path("description").asText(""),
                            taskNode.path("acceptanceCriterion").asText(""),
                            taskNode.path("roleTag").asText("backend"),
                            0,
                            "PLANNED",
                            null
                    );
                    tasks.add(task);
                }

                story.setTasks(tasks);
                stories.add(story);
            }

        } catch (Exception e) {
            // parsing failed — return empty, orchestrator will retry
        }

        return stories;
    }

    /** Fix 4: re-sends the full original prompt so the retry has at least as much context as the first call. */
    private String buildRetryPrompt(String originalPrompt, String previousOutput) {
        return "Your previous response was invalid JSON or violated the schema. "
                + "Return only valid JSON matching the required shape.\n\n"
                + originalPrompt
                + "\n\nPrevious (invalid) response for reference:\n"
                + previousOutput;
    }

    // ── Validation ───────────────────────────────────────────────────────────

    private boolean isValid(List<UserStory> stories) {
        if (stories == null || stories.isEmpty()) return false;

        for (UserStory story : stories) {
            if (story.getTitle() == null || story.getTitle().isBlank()) return false;
            // Fix 1: a story with exactly 1 task is valid (single-role epics e.g. backend-only)
            if (story.getTasks() == null || story.getTasks().size() < 1) return false;

            for (Task task : story.getTasks()) {
                if (task.getTitle() == null || task.getTitle().isBlank()) return false;
                if (task.getAcceptanceCriterion() == null || task.getAcceptanceCriterion().isBlank()) return false;
                if (!List.of("backend", "frontend", "qa", "devops", "android", "ios").contains(task.getRoleTag())) return false;
            }
        }

        return true;
    }

    // ── Structured output schema ──────────────────────────────────────────────

    private Schema buildBreakdownSchema() {
        Schema taskSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "title",               Schema.builder().type(Type.Known.STRING).build(),
                        "description",         Schema.builder().type(Type.Known.STRING).build(),
                        "acceptanceCriterion", Schema.builder().type(Type.Known.STRING).build(),
                        "roleTag",             Schema.builder().type(Type.Known.STRING)
                                                       .enum_(List.of("backend", "frontend", "qa", "devops", "android", "ios"))
                                                       .build()
                ))
                .required(List.of("title", "description", "acceptanceCriterion", "roleTag"))
                .build();

        Schema storySchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "epicTitle", Schema.builder().type(Type.Known.STRING).build(),
                        "title",     Schema.builder().type(Type.Known.STRING).build(),
                        "tasks",     Schema.builder().type(Type.Known.ARRAY).items(taskSchema).build()
                ))
                .required(List.of("epicTitle", "title", "tasks"))
                .build();

        return Schema.builder()
                .type(Type.Known.ARRAY)
                .items(storySchema)
                .build();
    }
}
