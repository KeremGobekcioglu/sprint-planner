package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.DevCompletion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DevCompletionJpaRepository extends JpaRepository<DevCompletion, UUID> {

    List<DevCompletion> findByReviewId(UUID reviewId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM DevCompletion")
    void deleteAllBulk();
}

