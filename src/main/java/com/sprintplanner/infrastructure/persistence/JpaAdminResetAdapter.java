package com.sprintplanner.infrastructure.persistence;

import com.sprintplanner.domain.port.out.AdminResetPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAdminResetAdapter implements AdminResetPort {

    private final DevCompletionJpaRepository devCompletionJpaRepository;
    private final EditLogJpaRepository editLogJpaRepository;
    private final TaskJpaRepository taskJpaRepository;
    private final UserStoryJpaRepository userStoryJpaRepository;
    private final SprintReviewJpaRepository sprintReviewJpaRepository;
    private final SprintJpaRepository sprintJpaRepository;

    public JpaAdminResetAdapter(
            DevCompletionJpaRepository devCompletionJpaRepository,
            EditLogJpaRepository editLogJpaRepository,
            TaskJpaRepository taskJpaRepository,
            UserStoryJpaRepository userStoryJpaRepository,
            SprintReviewJpaRepository sprintReviewJpaRepository,
            SprintJpaRepository sprintJpaRepository) {
        this.devCompletionJpaRepository = devCompletionJpaRepository;
        this.editLogJpaRepository = editLogJpaRepository;
        this.taskJpaRepository = taskJpaRepository;
        this.userStoryJpaRepository = userStoryJpaRepository;
        this.sprintReviewJpaRepository = sprintReviewJpaRepository;
        this.sprintJpaRepository = sprintJpaRepository;
    }

    @Override
    @Transactional
    public void resetAllSprintData() {
        devCompletionJpaRepository.deleteAllBulk();
        editLogJpaRepository.deleteAllBulk();
        taskJpaRepository.deleteAllBulk();
        userStoryJpaRepository.deleteAllBulk();
        sprintReviewJpaRepository.deleteAllBulk();
        sprintJpaRepository.deleteAllBulk();
    }
}
