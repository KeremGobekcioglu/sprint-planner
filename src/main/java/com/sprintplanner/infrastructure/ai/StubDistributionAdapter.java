package com.sprintplanner.infrastructure.ai;

import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.out.DistributionPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class StubDistributionAdapter implements DistributionPort {

    @Override
    public SprintPlan distribute(
        UUID teamId,
        String sprintGoal,
        List<UserStory> estimatedUserStories,
        Map<UUID, Integer> capacityByDeveloper,
        String teamProfileMemory,
        String sprintHistoryMemory
    ) {
        List<UserStory> userStories = estimatedUserStories == null ? List.of() : estimatedUserStories;
        if (capacityByDeveloper == null || capacityByDeveloper.isEmpty()) {
            return new SprintPlan(teamId, sprintGoal, userStories);
        }

        List<UUID> orderedDevelopers = capacityByDeveloper.entrySet()
            .stream()
            .sorted(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()))
            .map(Map.Entry::getKey)
            .toList();

        Map<UUID, Integer> remaining = new java.util.LinkedHashMap<>(capacityByDeveloper);
        int pointer = 0;

        for (UserStory story : userStories) {
            List<Task> tasks = story.getTasks() == null ? new ArrayList<>() : story.getTasks();
            for (Task task : tasks) {
                UUID selectedDev = selectDeveloper(orderedDevelopers, remaining, task.getStoryPoints(), pointer);
                task.setAssignedDevId(selectedDev);
                if (selectedDev != null) {
                    int capacity = remaining.getOrDefault(selectedDev, 0);
                    remaining.put(selectedDev, Math.max(0, capacity - Math.max(1, task.getStoryPoints())));
                    pointer = (pointer + 1) % orderedDevelopers.size();
                }
            }
        }

        return new SprintPlan(teamId, sprintGoal, userStories);
    }

    private UUID selectDeveloper(List<UUID> developerOrder, Map<UUID, Integer> remaining, int neededPoints, int start) {
        if (developerOrder.isEmpty()) {
            return null;
        }

        int safeNeeded = Math.max(1, neededPoints);
        for (int i = 0; i < developerOrder.size(); i++) {
            UUID candidate = developerOrder.get((start + i) % developerOrder.size());
            if (remaining.getOrDefault(candidate, 0) >= safeNeeded) {
                return candidate;
            }
        }

        // If nobody has enough capacity, still assign to the highest-capacity developer.
        return developerOrder.get(0);
    }
}
