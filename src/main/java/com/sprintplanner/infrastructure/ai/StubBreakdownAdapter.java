package com.sprintplanner.infrastructure.ai;

import com.sprintplanner.domain.dto.Epic;
import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.out.BreakdownPort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
//@Primary
public class StubBreakdownAdapter implements BreakdownPort {

    @Override
    public List<UserStory> breakdown(PlanningRequest request, String teamProfileMemory, String sprintHistoryMemory, int totalCapacity) {
        List<UserStory> stories = new ArrayList<>();
        List<Epic> epics = request.epics() == null ? List.of() : request.epics();

        if (epics.isEmpty()) {
            stories.add(createFallbackStory("General", "Prepare sprint foundation and backlog hygiene"));
            return stories;
        }

        for (Epic epic : epics) {
            String epicTitle = epic.title() == null || epic.title().isBlank() ? "Untitled Epic" : epic.title();
            stories.add(createStoryForEpic(epicTitle));
        }

        return stories;
    }

    private UserStory createStoryForEpic(String epicTitle) {
        UserStory story = new UserStory(
            UUID.randomUUID(),
            null,
            epicTitle,
            "Deliver " + epicTitle + " core flow",
            0,
            "PLANNED"
        );

        List<Task> tasks = new ArrayList<>();
        tasks.add(new Task(
            UUID.randomUUID(),
            story.getId(),
            null,
            "Design API contracts",
            "Define endpoint contracts and request/response models for " + epicTitle + ".",
            "Contracts reviewed and approved by backend + frontend.",
            "backend",
            0,
            "PLANNED",
            null
        ));
        tasks.add(new Task(
            UUID.randomUUID(),
            story.getId(),
            null,
            "Implement UI flow",
            "Build the main screen and interaction flow for " + epicTitle + ".",
            "UI flow works end-to-end against mocked API responses.",
            "frontend",
            0,
            "PLANNED",
            null
        ));
        tasks.add(new Task(
            UUID.randomUUID(),
            story.getId(),
            null,
            "Write acceptance tests",
            "Add functional coverage for " + epicTitle + " happy path and key edge cases.",
            "All acceptance tests pass in CI.",
            "qa",
            0,
            "PLANNED",
            null
        ));

        story.setTasks(tasks);
        return story;
    }

    private UserStory createFallbackStory(String epicTitle, String title) {
        UserStory story = new UserStory(
            UUID.randomUUID(),
            null,
            epicTitle,
            title,
            0,
            "PLANNED"
        );
        List<Task> tasks = new ArrayList<>();
        tasks.add(new Task(
            UUID.randomUUID(),
            story.getId(),
            null,
            "Refine backlog",
            "Review open items and clarify acceptance criteria.",
            "All stories have clear acceptance criteria and owners.",
            "backend",
            0,
            "PLANNED",
            null
        ));
        story.setTasks(tasks);
        return story;
    }
}
