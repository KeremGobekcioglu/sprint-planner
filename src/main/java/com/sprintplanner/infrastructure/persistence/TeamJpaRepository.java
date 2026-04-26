package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.Team;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamJpaRepository extends JpaRepository<Team, UUID> {
}
