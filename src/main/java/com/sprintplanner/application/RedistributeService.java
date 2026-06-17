package com.sprintplanner.application;

import com.sprintplanner.domain.dto.SprintPlan;
import com.sprintplanner.domain.dto.SprintPlanMapper;
import com.sprintplanner.domain.dto.TaskDto;
import com.sprintplanner.domain.dto.UserStoryDto;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.model.Task;
import com.sprintplanner.domain.model.Team;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.out.CapacityPort;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.DistributionPort;
import com.sprintplanner.domain.port.out.MemoryReader;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.domain.port.out.TeamRepository;
import com.sprintplanner.domain.service.CapabilityInferenceService;
import com.sprintplanner.infrastructure.ai.GeminiDistributionAdapter;
import com.sprintplanner.infrastructure.persistence.TaskJpaRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RedistributeService {

    private static final Logger logger = LoggerFactory.getLogger(RedistributeService.class);

    private final SprintRepository sprintRepository;
    private final DeveloperRepository developerRepository;
    private final TeamRepository teamRepository;
    private final CapacityPort capacityPort;
    private final DistributionPort distributionPort;
    private final MemoryReader memoryReader;
    private final TaskJpaRepository taskJpaRepository;

    public RedistributeService(
            SprintRepository sprintRepository,
            DeveloperRepository developerRepository,
            TeamRepository teamRepository,
            CapacityPort capacityPort,
            DistributionPort distributionPort,
            MemoryReader memoryReader,
            TaskJpaRepository taskJpaRepository) {
        this.sprintRepository = sprintRepository;
        this.developerRepository = developerRepository;
        this.teamRepository = teamRepository;
        this.capacityPort = capacityPort;
        this.distributionPort = distributionPort;
        this.memoryReader = memoryReader;
        this.taskJpaRepository = taskJpaRepository;
    }

    @Transactional
    public SprintPlan redistribute(UUID sprintId) {
        Sprint sprint = sprintRepository.findWithStoriesAndTasksById(sprintId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found: " + sprintId));

        if (sprint.getStatus() != Sprint.Status.PLANNING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot redistribute a sprint that is not in PLANNING status (current: " + sprint.getStatus() + ")");
        }

        // Reset all non-carry-over task assignments so the distribution runs from scratch
        if (sprint.getUserStories() != null) {
            for (UserStory story : sprint.getUserStories()) {
                boolean isCarryover = GeminiDistributionAdapter.CARRYOVER_EPIC_TITLE.equals(story.getEpicTitle())
                    || GeminiDistributionAdapter.CARRYOVER_EPIC_TITLE.equals(story.getTitle());
                if (isCarryover || story.getTasks() == null) continue;
                for (Task task : story.getTasks()) {
                    task.setAssignedDevId(null);
                    task.setAssignmentReason(null);
                    taskJpaRepository.save(task);
                }
            }
        }

        List<Developer> developers = developerRepository.findByTeamId(sprint.getTeamId());
        Team team = teamRepository.findById(sprint.getTeamId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found: " + sprint.getTeamId()));

        Map<UUID, List<String>> developerCapabilities = developers.stream()
            .collect(Collectors.toMap(
                Developer::getId,
                dev -> dev.getCapabilities().isEmpty()
                    ? CapabilityInferenceService.inferFromRole(dev.getRole())
                    : dev.getCapabilities(),
                (a, b) -> a,
                LinkedHashMap::new
            ));

        Map<UUID, String> developerNames = developers.stream()
            .collect(Collectors.toMap(
                Developer::getId,
                Developer::getName,
                (a, b) -> a,
                LinkedHashMap::new
            ));

        Map<UUID, String> developerSeniorities = developers.stream()
            .collect(Collectors.toMap(
                Developer::getId,
                dev -> dev.getSeniority() != null ? dev.getSeniority() : "MID",
                (a, b) -> a,
                LinkedHashMap::new
            ));

        String teamProfileMemory = memoryReader.readTeamProfile(sprint.getTeamId());
        String sprintHistoryMemory = memoryReader.readSprintHistory(sprint.getTeamId());

        Map<UUID, Integer> capacityByDeveloper = capacityPort.calculateCapacity(
            developers,
            team.getSprintLengthDays(),
            Map.of(),
            sprintHistoryMemory,
            sprint.getTeamId()
        );

        // Separate carry-over stories (keep their assignments) from distributable ones
        List<UserStory> storiesForDistribution = new ArrayList<>();
        List<UserStory> carryoverStories = new ArrayList<>();
        if (sprint.getUserStories() != null) {
            for (UserStory story : sprint.getUserStories()) {
                if (GeminiDistributionAdapter.CARRYOVER_EPIC_TITLE.equals(story.getEpicTitle())
                        || GeminiDistributionAdapter.CARRYOVER_EPIC_TITLE.equals(story.getTitle())) {
                    carryoverStories.add(story);
                } else {
                    storiesForDistribution.add(story);
                }
            }
        }

        // Reserve carry-over capacity before distribution
        Map<UUID, Integer> capacityForDistribution = new LinkedHashMap<>(capacityByDeveloper);
        for (UserStory story : carryoverStories) {
            if (story.getTasks() == null) continue;
            for (Task task : story.getTasks()) {
                if (task.getAssignedDevId() != null) {
                    capacityForDistribution.merge(task.getAssignedDevId(), -task.getStoryPoints(), Integer::sum);
                }
            }
        }

        logger.info("[REDISTRIBUTE] sprintId={} — running shuffled distribution for {} stories",
            sprintId, storiesForDistribution.size());

        SprintPlan result = distributionPort.distribute(
            sprint.getTeamId(),
            sprint.getGoal(),
            storiesForDistribution,
            capacityForDistribution,
            developerCapabilities,
            developerNames,
            developerSeniorities,
            teamProfileMemory,
            sprintHistoryMemory,
            true
        );

        // Persist the new assignments written by the distribution into task entities
        if (sprint.getUserStories() != null) {
            for (UserStory story : sprint.getUserStories()) {
                if (story.getTasks() == null) continue;
                for (Task task : story.getTasks()) {
                    taskJpaRepository.save(task);
                }
            }
        }

        logger.info("[REDISTRIBUTE] sprintId={} — distribution complete, persisted assignments", sprintId);

        // Merge carry-over stories back with original assignments preserved
        if (!carryoverStories.isEmpty()) {
            List<UserStoryDto> allStories = new ArrayList<>(result.userStories());
            List<TaskDto> allUnassigned = new ArrayList<>(result.unassignedTasks());
            for (UserStory story : carryoverStories) {
                List<TaskDto> assignedTaskDtos = new ArrayList<>();
                if (story.getTasks() != null) {
                    for (Task task : story.getTasks()) {
                        if (task.getAssignedDevId() != null) {
                            task.setAssignmentReason("Carry-over — reassigned to original developer");
                            assignedTaskDtos.add(SprintPlanMapper.toDto(task));
                        } else {
                            task.setAssignmentReason("Carry-over — was unassigned in previous sprint, assign manually");
                            allUnassigned.add(SprintPlanMapper.toDto(task));
                        }
                    }
                }
                int storyPts = assignedTaskDtos.stream().mapToInt(TaskDto::storyPoints).sum();
                allStories.add(new UserStoryDto(
                    story.getId(), story.getSprintId(), story.getEpicTitle(),
                    story.getTitle(), storyPts, story.getStatus(), assignedTaskDtos
                ));
            }
            result = new SprintPlan(result.teamId(), result.sprintGoal(), allStories, allUnassigned,
                result.sprintStatus(), result.confidence());
        }

        return result;
    }
}
