package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.model.EditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EditLogJpaRepository extends JpaRepository<EditLog, UUID> {

    List<EditLog> findBySprintId(UUID sprintId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM EditLog")
    void deleteAllBulk();
}
