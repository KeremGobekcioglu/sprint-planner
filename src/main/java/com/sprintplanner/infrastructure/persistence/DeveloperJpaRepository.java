package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.Developer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeveloperJpaRepository extends JpaRepository<Developer, UUID> {

    List<Developer> findByTeamId(UUID teamId);

    long deleteByIdAndTeamId(UUID id, UUID teamId);
}
