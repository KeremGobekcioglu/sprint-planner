package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.Task;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskJpaRepository extends JpaRepository<Task, UUID> {
}
