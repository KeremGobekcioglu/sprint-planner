package com.sprintplanner.domain.port.out;

import com.sprintplanner.domain.model.EditLog;
import java.util.List;
import java.util.UUID;

/**
 * Port for persisting edit logs.
 * Implemented by infrastructure layer.
 */
public interface EditLogRepository {

    EditLog save(EditLog editLog);

    List<EditLog> findBySprintId(UUID sprintId);
}
