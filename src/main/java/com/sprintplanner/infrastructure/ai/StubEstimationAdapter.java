package com.sprintplanner.infrastructure.ai;

import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.out.EstimationPort;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class StubEstimationAdapter implements EstimationPort {

    @Override
    public List<UserStory> estimate(List<UserStory> userStories, String sprintHistoryMemory) {
        if (userStories == null) {
            return List.of();
        }

        for (UserStory story : userStories) {
            int storyPoints = 0;
            if (story.getTasks() != null) {
                for (Task task : story.getTasks()) {
                    int points = estimateTaskPoints(task);
                    task.setStoryPoints(points);
                    storyPoints += points;
                }
            }
            story.setStoryPoints(toNearestFibonacci(storyPoints));
        }

        return userStories;
    }

    private int estimateTaskPoints(Task task) {
        if (task == null || task.getRoleTag() == null) {
            return 2;
        }

        return switch (task.getRoleTag().toLowerCase()) {
            case "backend" -> 5;
            case "frontend" -> 3;
            case "qa" -> 2;
            case "devops" -> 3;
            default -> 2;
        };
    }

    private int toNearestFibonacci(int value) {
        int[] fibonacci = {1, 2, 3, 5, 8, 13};
        int best = fibonacci[0];
        int bestDistance = Math.abs(value - best);
        for (int i = 1; i < fibonacci.length; i++) {
            int candidate = fibonacci[i];
            int distance = Math.abs(value - candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }
}
