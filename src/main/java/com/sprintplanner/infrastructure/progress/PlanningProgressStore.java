package com.sprintplanner.infrastructure.progress;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.sprintplanner.domain.port.out.ProgressPort;
import org.springframework.stereotype.Component;

/**
 * In-memory store for real-time planning pipeline progress.
 * One entry list per user. Cleared when planning starts.
 */
@Component
public class PlanningProgressStore implements ProgressPort {

    public record Step(String phase, String message, Instant at, boolean done, boolean error) {}

    private final ConcurrentHashMap<UUID, List<Step>> store = new ConcurrentHashMap<>();

    public void start(UUID userId) {
        store.put(userId, Collections.synchronizedList(new ArrayList<>()));
    }

    public void push(UUID userId, String phase, String message) {
        store.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()))
             .add(new Step(phase, message, Instant.now(), false, false));
    }

    public void complete(UUID userId) {
        push(userId, "done", "Sprint plan ready.");
        List<Step> steps = store.get(userId);
        if (steps != null && !steps.isEmpty()) {
            int last = steps.size() - 1;
            Step old = steps.get(last);
            steps.set(last, new Step(old.phase(), old.message(), old.at(), true, false));
        }
    }

    public void fail(UUID userId, String message) {
        store.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()))
             .add(new Step("error", message, Instant.now(), true, true));
    }

    public List<Step> get(UUID userId) {
        List<Step> steps = store.get(userId);
        return steps == null ? List.of() : List.copyOf(steps);
    }

    public void clear(UUID userId) {
        store.remove(userId);
    }
}
