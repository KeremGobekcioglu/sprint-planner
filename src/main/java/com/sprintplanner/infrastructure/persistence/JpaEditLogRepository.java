package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.EditLog;
import com.sprintplanner.domain.port.out.EditLogRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEditLogRepository implements EditLogRepository {

    private final EditLogJpaRepository editLogJpaRepository;

    public JpaEditLogRepository(EditLogJpaRepository editLogJpaRepository) {
        this.editLogJpaRepository = editLogJpaRepository;
    }

    @Override
    public EditLog save(EditLog editLog) {
        return editLogJpaRepository.save(editLog);
    }

    @Override
    public List<EditLog> findBySprintId(UUID sprintId) {
        return editLogJpaRepository.findBySprintId(sprintId);
    }
}
