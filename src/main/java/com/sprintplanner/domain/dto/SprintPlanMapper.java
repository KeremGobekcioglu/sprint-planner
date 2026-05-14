package com.sprintplanner.domain.dto;

import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.model.Sprint;
import java.util.ArrayList;
import java.util.List;

/**
 * Static utility mapper for converting JPA entities to lightweight DTOs.
 * Breaks circular references before serialization.
 */
public class SprintPlanMapper {

    private SprintPlanMapper() {
        // Static utility class
    }

    /**
     * Convert a Task entity to a TaskDto, excluding back-references.
     */
    public static TaskDto toDto(Task task) {
        if (task == null) {
            return null;
        }
        return new TaskDto(
            task.getId(),
            task.getStoryId(),
            task.getAssignedDevId(),
            task.getTitle(),
            task.getDescription(),
            task.getAcceptanceCriterion(),
            task.getRoleTag(),
            task.getStoryPoints(),
            task.getStatus(),
            task.getTrelloCardId()
        );
    }

    /**
     * Convert a UserStory entity to a UserStoryDto, including nested TaskDtos.
     */
    public static UserStoryDto toDto(UserStory story) {
        if (story == null) {
            return null;
        }
        List<TaskDto> taskDtos = new ArrayList<>();
        if (story.getTasks() != null) {
            for (Task task : story.getTasks()) {
                taskDtos.add(toDto(task));
            }
        }
        return new UserStoryDto(
            story.getId(),
            story.getSprintId(),
            story.getEpicTitle(),
            story.getTitle(),
            story.getStoryPoints(),
            story.getStatus(),
            taskDtos
        );
    }

    /**
     * Convert a Sprint entity to a SprintPlan, mapping user stories via toDto.
     */
    public static SprintPlan toSprintPlan(Sprint sprint) {
        if (sprint == null) {
            return null;
        }
        List<UserStoryDto> storyDtos = new ArrayList<>();
        if (sprint.getUserStories() != null) {
            for (UserStory story : sprint.getUserStories()) {
                storyDtos.add(toDto(story));
            }
        }
        return new SprintPlan(
            sprint.getTeamId(),
            sprint.getGoal(),
            storyDtos,
            List.of()
        );
    }

    /**
     * Convert a TaskDto back to a Task entity (for persistence).
     * Note: back-references (story) are not set by this method.
     */
    public static Task fromDto(TaskDto dto) {
        if (dto == null) {
            return null;
        }
        return new Task(
            dto.id(),
            dto.storyId(),
            dto.assignedDevId(),
            dto.title(),
            dto.description(),
            dto.acceptanceCriterion(),
            dto.roleTag(),
            dto.storyPoints(),
            dto.status(),
            dto.trelloCardId()
        );
    }

    /**
     * Convert a UserStoryDto back to a UserStory entity (for persistence).
     * Note: back-reference (sprint) is not set by this method.
     */
    public static UserStory fromDto(UserStoryDto dto) {
        if (dto == null) {
            return null;
        }
        UserStory story = new UserStory(
            dto.id(),
            dto.sprintId(),
            dto.epicTitle(),
            dto.title(),
            dto.storyPoints(),
            dto.status()
        );
        if (dto.tasks() != null) {
            List<Task> tasks = new ArrayList<>();
            for (TaskDto taskDto : dto.tasks()) {
                tasks.add(fromDto(taskDto));
            }
            story.setTasks(tasks);
        }
        return story;
    }
}
