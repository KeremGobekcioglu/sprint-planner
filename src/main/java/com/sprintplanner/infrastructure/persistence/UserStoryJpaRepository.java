package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.UserStory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserStoryJpaRepository extends JpaRepository<UserStory, UUID> {

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserStory")
    void deleteAllBulk();
}
