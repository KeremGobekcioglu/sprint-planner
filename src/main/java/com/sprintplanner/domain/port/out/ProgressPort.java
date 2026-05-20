package com.sprintplanner.domain.port.out;

import java.util.UUID;

public interface ProgressPort {
    void start(UUID userId);
    void push(UUID userId, String phase, String message);
    void complete(UUID userId);
    void fail(UUID userId, String message);
}
