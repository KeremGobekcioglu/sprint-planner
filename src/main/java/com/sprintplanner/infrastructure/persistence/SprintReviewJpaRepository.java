package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.SprintReview;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintReviewJpaRepository extends JpaRepository<SprintReview, UUID> {}

