package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.SprintReview;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SprintReviewJpaRepository extends JpaRepository<SprintReview, UUID> {

    boolean existsBySprintId(UUID sprintId);

    Optional<SprintReview> findBySprintId(UUID sprintId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM SprintReview")
    void deleteAllBulk();
}

