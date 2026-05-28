package com.sprintplanner.infrastructure.publisher;

import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.infrastructure.external.TrelloApiClient;
import com.sprintplanner.infrastructure.persistence.TaskJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TrelloPlanPublisher {

    private static final Logger logger = LoggerFactory.getLogger(TrelloPlanPublisher.class);

    private final TrelloApiClient trelloApiClient;
    private final SprintRepository sprintRepository;
    private final TaskJpaRepository taskJpaRepository;
    private final DeveloperRepository developerRepository;

    public TrelloPlanPublisher(TrelloApiClient trelloApiClient, SprintRepository sprintRepository,
            TaskJpaRepository taskJpaRepository, DeveloperRepository developerRepository) {
        this.trelloApiClient = trelloApiClient;
        this.sprintRepository = sprintRepository;
        this.taskJpaRepository = taskJpaRepository;
        this.developerRepository = developerRepository;
    }

    public void publishSprint(UUID sprintId, String trelloBoardId) {
        if (trelloBoardId == null || trelloBoardId.isBlank()) {
            throw new IllegalArgumentException("No Trello board linked to this team");
        }

        Sprint sprint = sprintRepository.findWithStoriesAndTasksById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint not found: " + sprintId));

        if (sprint.getUserStories() == null || sprint.getUserStories().isEmpty()) {
            logger.warn("Sprint {} has no user stories — nothing to push", sprintId);
            return;
        }

        for (UserStory story : sprint.getUserStories()) {
            String listId;
            try {
                listId = trelloApiClient.findOrCreateList(trelloBoardId, story.getEpicTitle());
            } catch (Exception e) {
                logger.error("Failed to create/find list for epic '{}': {}", story.getEpicTitle(), e.getMessage());
                continue;
            }

            if (story.getTasks() == null) continue;
            int cardCount = 0;

            for (Task task : story.getTasks()) {
                try {
                    String devName = "Unassigned";
                    String trelloMemberId = null;
                    if (task.getAssignedDevId() != null) {
                        Optional<Developer> devOpt = developerRepository.findById(task.getAssignedDevId());
                        if (devOpt.isPresent()) {
                            Developer dev = devOpt.get();
                            devName = dev.getName();
                            trelloMemberId = dev.getTrelloMemberId();
                        }
                    }
                    String desc = buildCardDescription(task, devName);
                    String cardId = trelloApiClient.createCard(listId, task.getTitle(), desc, trelloMemberId);
                    task.setTrelloCardId(cardId);
                    taskJpaRepository.save(task);
                    cardCount++;
                } catch (Exception e) {
                    logger.warn("Failed to push task '{}': {}", task.getTitle(), e.getMessage());
                }
            }

            logger.info("[TRELLO] Pushed story '{}' → {} cards created", story.getTitle(), cardCount);
        }
    }

    private String buildCardDescription(Task task, String developerName) {
        StringBuilder sb = new StringBuilder();
        if (task.getDescription() != null && !task.getDescription().isBlank()) {
            sb.append(task.getDescription()).append("\n\n");
        }
        sb.append("**Acceptance Criteria:**\n");
        sb.append(task.getAcceptanceCriterion() != null ? task.getAcceptanceCriterion() : "None");
        sb.append("\n\n**Assigned to:** ").append(developerName);
        sb.append("\n**Role:** ").append(task.getRoleTag());
        sb.append("\n**Story Points:** ").append(task.getStoryPoints());
        return sb.toString();
    }
}
