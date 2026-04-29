package com.sprintplanner.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.sprintplanner.domain.dto.Epic;
import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.out.BreakdownPort;
import com.sprintplanner.infrastructure.config.GeminiConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Primary
public class GeminiBreakdownAdapter implements BreakdownPort {

    private final Client geminiClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public GeminiBreakdownAdapter(GeminiConfig geminiConfig, ObjectMapper objectMapper) {
        this.geminiClient = Client.builder().apiKey(geminiConfig.getApiKey()).build();
        this.model = geminiConfig.getModel();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<UserStory> breakdown(
            PlanningRequest request,
            String teamProfileMemory,
            String sprintHistoryMemory
    ) {
        List<UserStory> allStories = new ArrayList<>();

        List<Epic> epics = request.epics() == null ? List.of() : request.epics();
        for (Epic epic : epics) {
            String prompt = buildPrompt(epic, request, teamProfileMemory, sprintHistoryMemory);
            String raw = callGemini(prompt);
            List<UserStory> stories = parseResponse(raw, epic.title());

            // Retry once if validation fails
            if (!isValid(stories)) {
                String retryPrompt = buildRetryPrompt(raw, epic.title());
                String retryRaw = callGemini(retryPrompt);
                stories = parseResponse(retryRaw, epic.title());
            }

            allStories.addAll(stories);
        }

        return allStories;
    }

    // ── Prompt ───────────────────────────────────────────────────────────────

    private String buildPrompt(Epic epic, PlanningRequest request,
                               String teamProfile, String sprintHistory) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert agile sprint planner. Your job is to break down a product epic into user stories and tasks.\n\n");

        sb.append("## Domain\n").append(request.domain()).append("\n\n");
        sb.append("## Sprint Goal\n").append(request.sprintGoal()).append("\n\n");

        sb.append("## Epic\n");
        sb.append("Title: ").append(epic.title()).append("\n");
        if (epic.description() != null && !epic.description().isBlank()) {
            sb.append("Description: ").append(epic.description()).append("\n");
        }
        sb.append("\n");

        if (teamProfile != null && !teamProfile.isBlank()) {
            sb.append("## Team Preferences (follow these)\n").append(teamProfile).append("\n\n");
        }

        if (sprintHistory != null && !sprintHistory.isBlank()) {
            sb.append("## Sprint History (last 3 sprints)\n").append(sprintHistory).append("\n\n");
        }

        sb.append("## Domain-Specific Rules\n");
        switch (request.domain()) {
            case "FINTECH" -> sb.append(
                    "- All tasks must have strict, testable acceptance criteria.\n" +
                            "- Include mandatory review/approval steps.\n" +
                            "- Include compliance or error-handling tasks where relevant.\n" +
                            "- Estimates should be conservative — fintech tasks are rarely trivial.\n"
            );
            case "E_COMMERCE" -> sb.append(
                    "- Emphasise A/B test tasks and analytics event tracking.\n" +
                            "- Mobile-first UI tasks should be explicit.\n" +
                            "- Include QA tasks for checkout and payment flows.\n"
            );
            default -> sb.append(
                    "- Use balanced, neutral breakdown.\n" +
                            "- Flag ambiguous requirements with a note in the description.\n"
            );
        }
        sb.append("\n");

        sb.append("## Output Rules\n");
        sb.append("- Respond ONLY with a valid JSON array. No explanation, no markdown, no backticks.\n");
        sb.append("- Each element is a user story object.\n");
        sb.append("- Each user story must have: title (string), tasks (array of 2-4 task objects).\n");
        sb.append("- Each task must have: title (string), description (string), acceptanceCriterion (string), roleTag (one of: backend, frontend, qa, devops).\n");
        sb.append("- Produce 1-3 user stories per epic.\n");
        sb.append("- Do not include story points — those will be added by the estimation agent.\n\n");

        sb.append("## Example Output Shape\n");
        sb.append("""
            [
              {
                "title": "User can complete IBAN transfer",
                "tasks": [
                  {
                    "title": "Implement IBAN validation endpoint",
                    "description": "POST /transfers/validate-iban — validate format and checksum.",
                    "acceptanceCriterion": "Invalid IBANs return 400 with error code. Valid IBANs return 200.",
                    "roleTag": "backend"
                  },
                  {
                    "title": "Build transfer form UI",
                    "description": "Form with IBAN input, amount, and confirmation step.",
                    "acceptanceCriterion": "User can submit a valid transfer. Invalid IBAN shows inline error.",
                    "roleTag": "frontend"
                  }
                ]
              }
            ]
            """);

        return sb.toString();
    }

    private String buildRetryPrompt(String previousOutput, String epicTitle) {
        return """
            Your previous response for epic "%s" was invalid or incomplete.
            Previous output: %s
            
            Requirements that were not met:
            - Each user story must have a title and 2-4 tasks.
            - Each task must have: title, description, acceptanceCriterion, roleTag.
            - roleTag must be one of: backend, frontend, qa, devops.
            - Response must be a raw JSON array only — no markdown, no backticks.
            
            Please produce a corrected response now.
            """.formatted(epicTitle, previousOutput);
    }

    // ── Gemini call ──────────────────────────────────────────────────────────

    private String callGemini(String prompt) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(0.4f)
                .build();

        GenerateContentResponse response = geminiClient.models.generateContent(
                model, prompt, config
        );

        return response.text();
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    private List<UserStory> parseResponse(String raw, String epicTitle) {
        List<UserStory> stories = new ArrayList<>();

        try {
            String cleaned = raw.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isArray()) return stories;

            for (JsonNode storyNode : root) {
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
            // Parsing failed — return empty list, orchestrator will retry or halt
        }

        return stories;
    }

    // ── Validation ───────────────────────────────────────────────────────────

    private boolean isValid(List<UserStory> stories) {
        if (stories == null || stories.isEmpty()) return false;

        for (UserStory story : stories) {
            if (story.getTitle() == null || story.getTitle().isBlank()) return false;
            if (story.getTasks() == null || story.getTasks().size() < 2) return false;

            for (Task task : story.getTasks()) {
                if (task.getTitle() == null || task.getTitle().isBlank()) return false;
                if (task.getAcceptanceCriterion() == null || task.getAcceptanceCriterion().isBlank()) return false;
                if (!List.of("backend", "frontend", "qa", "devops").contains(task.getRoleTag())) return false;
            }
        }

        return true;
    }
}