package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.Sprint;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintJpaRepository extends JpaRepository<Sprint, UUID> {

    List<Sprint> findByTeamId(UUID teamId);
}
