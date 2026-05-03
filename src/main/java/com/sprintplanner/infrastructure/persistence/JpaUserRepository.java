package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for AppUser
 */
public interface JpaUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);
}

