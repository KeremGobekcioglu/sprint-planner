package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.DevCompletion;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevCompletionJpaRepository extends JpaRepository<DevCompletion, UUID> {
}

