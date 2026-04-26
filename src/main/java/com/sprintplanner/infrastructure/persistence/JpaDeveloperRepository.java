package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaDeveloperRepository implements DeveloperRepository {

    private final DeveloperJpaRepository developerJpaRepository;

    public JpaDeveloperRepository(DeveloperJpaRepository developerJpaRepository) {
        this.developerJpaRepository = developerJpaRepository;
    }

    @Override
    public Developer save(Developer developer) {
        return developerJpaRepository.save(developer);
    }

    @Override
    public Optional<Developer> findById(UUID developerId) {
        return developerJpaRepository.findById(developerId);
    }

    @Override
    public List<Developer> findByTeamId(UUID teamId) {
        return developerJpaRepository.findByTeamId(teamId);
    }

    @Override
    public boolean deleteByIdAndTeamId(UUID developerId, UUID teamId) {
        return developerJpaRepository.deleteByIdAndTeamId(developerId, teamId) > 0;
    }
}
