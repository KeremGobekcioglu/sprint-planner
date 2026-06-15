package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.Task;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TaskJpaRepository extends JpaRepository<Task, UUID> {

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Task")
    void deleteAllBulk();
}
