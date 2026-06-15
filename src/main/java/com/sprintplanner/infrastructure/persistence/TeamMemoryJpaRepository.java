package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.TeamMemory;
import com.sprintplanner.domain.model.TeamMemoryId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemoryJpaRepository extends JpaRepository<TeamMemory, TeamMemoryId> {
}
