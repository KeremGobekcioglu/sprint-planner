package com.sprintplanner.application;

import com.sprintplanner.domain.model.AppUser;
import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.model.SprintReview;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.domain.port.out.TeamRepository;
import com.sprintplanner.infrastructure.persistence.JpaUserRepository;
import com.sprintplanner.web.dto.ProfileResponse;
import com.sprintplanner.web.dto.ProfileStatsResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private final JpaUserRepository userRepository;
    private final TeamRepository teamRepository;
    private final SprintRepository sprintRepository;

    public ProfileService(
        JpaUserRepository userRepository,
        TeamRepository teamRepository,
        SprintRepository sprintRepository
    ) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.sprintRepository = sprintRepository;
    }

    public ProfileResponse getProfile(UUID userId) {
        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<TeamSummary> teams = teamRepository.findByOwnerId(userId).stream()
            .map(team -> {
                int developerCount = team.getDevelopers().size();
                long completedSprints = sprintRepository.countCompletedByTeamId(team.getId());

                LocalDateTime lastSprintAt = sprintRepository.findByTeamId(team.getId()).stream()
                    .max((a, b) -> a.getPlannedAt().compareTo(b.getPlannedAt()))
                    .map(s -> LocalDateTime.ofInstant(s.getPlannedAt(), ZoneId.systemDefault()))
                    .orElse(null);

                return new TeamSummary(
                    team.getId(),
                    team.getName(),
                    team.getDomain(),
                    developerCount,
                    (int) completedSprints,
                    lastSprintAt
                );
            })
            .toList();

        return new ProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getRole(),
            user.getCreatedAt(),
            teams
        );
    }

    public ProfileStatsResponse getStats(UUID userId) {
        List<Sprint> allSprints = teamRepository.findByOwnerId(userId).stream()
            .flatMap(team -> sprintRepository.findByTeamId(team.getId()).stream())
            .toList();

        int totalSprintsPlanned = (int) allSprints.stream()
            .filter(s -> !s.getStatus().equals(Sprint.Status.PLANNING))
            .count();

        int totalSprintsCompleted = (int) allSprints.stream()
            .filter(s -> s.getStatus().equals(Sprint.Status.COMPLETED))
            .count();

        double averageVelocityAccuracy = computeVelocityAccuracy(allSprints);
        int goalHitRate = computeGoalHitRate(allSprints);
        String mostActiveTeam = findMostActiveTeam(userId);

        return new ProfileStatsResponse(
            totalSprintsPlanned,
            totalSprintsCompleted,
            averageVelocityAccuracy,
            goalHitRate,
            mostActiveTeam
        );
    }

    public void updateName(UUID userId, String name) {
        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (name != null && !name.isBlank()) {
            user.setName(name.trim());
            userRepository.save(user);
        }
    }

    private double computeVelocityAccuracy(List<Sprint> sprints) {
        List<SprintReview> reviews = sprints.stream()
            .map(Sprint::getSprintReview)
            .filter(r -> r != null && r.getTotalPlannedPts() > 0)
            .toList();

        if (reviews.isEmpty()) {
            return 0.0;
        }

        double totalAccuracy = reviews.stream()
            .mapToDouble(r -> (double) r.getTotalCompletedPts() / r.getTotalPlannedPts())
            .sum();

        return totalAccuracy / reviews.size();
    }

    private int computeGoalHitRate(List<Sprint> sprints) {
        List<SprintReview> reviews = sprints.stream()
            .map(Sprint::getSprintReview)
            .filter(r -> r != null)
            .toList();

        if (reviews.isEmpty()) {
            return 0;
        }

        long goalYesCount = reviews.stream()
            .filter(r -> r.getGoalResult().equals(SprintReview.GoalResult.YES))
            .count();

        return (int) ((goalYesCount * 100) / reviews.size());
    }

    private String findMostActiveTeam(UUID userId) {
        return teamRepository.findByOwnerId(userId).stream()
            .map(team -> {
                long completedCount = sprintRepository.countCompletedByTeamId(team.getId());
                return Map.entry(team.getName(), completedCount);
            })
            .max((a, b) -> Long.compare(a.getValue(), b.getValue()))
            .map(Map.Entry::getKey)
            .orElse(null);
    }
}




