package com.sprintplanner.infrastructure.ai;

import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.dto.SprintPlanMapper;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.out.DistributionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
//@Primary
public class StubDistributionAdapter implements DistributionPort {

    private static final Logger logger = LoggerFactory.getLogger(StubDistributionAdapter.class);

    @Override
    public SprintPlan distribute(
        UUID teamId,
        String sprintGoal,
        List<UserStory> estimatedUserStories,
        Map<UUID, Integer> capacityByDeveloper,
        Map<UUID, List<String>> developerCapabilities,
        Map<UUID, String> developerNames,
        Map<UUID, String> developerSeniorities,
        String teamProfileMemory,
        String sprintHistoryMemory,
        boolean shuffle
    ) {
        List<UserStory> userStories = estimatedUserStories == null ? List.of() : estimatedUserStories;
        if (capacityByDeveloper == null || capacityByDeveloper.isEmpty()) {
            return convertToSprintPlan(teamId, sprintGoal, userStories, List.of());
        }

        List<UUID> orderedDevelopers = capacityByDeveloper.entrySet()
            .stream()
            .sorted(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()))
            .map(Map.Entry::getKey)
            .toList();

        Map<UUID, Integer> remaining = new LinkedHashMap<>(capacityByDeveloper);
        List<Task> unassignedTasks = new ArrayList<>();
        int pointer = 0;

        for (UserStory story : userStories) {
            List<Task> tasks = story.getTasks() == null ? List.of() : story.getTasks();
            for (Task task : tasks) {
                String roleTag = normalizeRole(task.getRoleTag());
                int needed = Math.max(1, task.getStoryPoints());

                // Step 1: Find developer with matching capability and sufficient capacity
                UUID selected = null;
                for (int i = 0; i < orderedDevelopers.size(); i++) {
                    UUID candidate = orderedDevelopers.get((pointer + i) % orderedDevelopers.size());
                    List<String> caps = developerCapabilities != null
                        ? developerCapabilities.getOrDefault(candidate, List.of())
                        : List.of();
                    boolean hasCapability = caps.stream().anyMatch(c -> normalizeRole(c).equals(roleTag));
                    if (hasCapability && remaining.getOrDefault(candidate, 0) >= needed) {
                        selected = candidate;
                        pointer = (orderedDevelopers.indexOf(selected) + 1) % orderedDevelopers.size();
                        break;
                    }
                }

                // Step 2: Cross-capability fallback if no capability match found
                if (selected == null) {
                    selected = findCrossCapabilityFallback(roleTag, needed, remaining, developerCapabilities, orderedDevelopers);
                }

                if (selected != null) {
                    task.setAssignedDevId(selected);
                    remaining.merge(selected, -needed, Integer::sum);
                    task.setAssignmentReason("Assigned by stub — best available " + task.getRoleTag() + " developer");
                } else {
                    task.setAssignedDevId(null);
                    task.setAssignmentReason("No developer with " + task.getRoleTag() + " capability or remaining capacity");
                    unassignedTasks.add(task);
                }
            }
        }

        return convertToSprintPlan(teamId, sprintGoal, userStories, unassignedTasks);
    }

    private UUID findCrossCapabilityFallback(
            String roleTag,
            int needed,
            Map<UUID, Integer> remaining,
            Map<UUID, List<String>> developerCapabilities,
            List<UUID> orderedDevelopers) {

        if (developerCapabilities == null) return null;

        boolean anyoneHasCapability = developerCapabilities.values().stream()
            .anyMatch(caps -> caps.stream().anyMatch(c -> normalizeRole(c).equals(roleTag)));
        if (anyoneHasCapability) return null;

        List<String> fallbackCapabilities = switch (roleTag) {
            case "qa" -> List.of("backend", "frontend", "fullstack");
            case "devops" -> List.of("backend", "fullstack");
            case "android" -> List.of("frontend", "fullstack", "backend");
            case "ios" -> List.of("frontend", "fullstack", "backend");
            default -> List.of();
        };

        for (String fallbackCap : fallbackCapabilities) {
            for (UUID candidate : orderedDevelopers) {
                List<String> caps = developerCapabilities.getOrDefault(candidate, List.of());
                boolean hasFallback = caps.stream().anyMatch(c -> normalizeRole(c).equals(fallbackCap));
                if (hasFallback && remaining.getOrDefault(candidate, 0) >= needed) {
                    logger.info("[STUB-DISTRIBUTION] Cross-capability fallback: {} task assigned via {} capability",
                        roleTag, fallbackCap);
                    return candidate;
                }
            }
        }

        return null;
    }

    private SprintPlan convertToSprintPlan(UUID teamId, String sprintGoal, List<UserStory> userStories, List<Task> unassignedTasks) {
        Set<UUID> unassignedIds = unassignedTasks == null || unassignedTasks.isEmpty()
            ? Set.of()
            : unassignedTasks.stream().map(Task::getId).collect(Collectors.toSet());

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

    private String normalizeRole(String role) {
        if (role == null) return "";
        return role.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
