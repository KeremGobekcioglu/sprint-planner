package com.sprintplanner.web;

import com.sprintplanner.application.ProfileService;
import com.sprintplanner.application.TeamSummary;
import com.sprintplanner.domain.model.Sprint;
import com.sprintplanner.domain.model.SprintReview;
import com.sprintplanner.domain.model.UserStory;
import com.sprintplanner.domain.port.out.SprintRepository;
import com.sprintplanner.web.dto.ActiveSprintSummary;
import com.sprintplanner.web.dto.DashboardResponse;
import com.sprintplanner.web.dto.RecentSprintSummary;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final SprintRepository sprintRepository;
    private final ProfileService profileService;

    public DashboardController(SprintRepository sprintRepository, ProfileService profileService) {
        this.sprintRepository = sprintRepository;
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard() {
        UUID userId = SecurityUtils.getCurrentUserId();

        List<TeamSummary> teams = profileService.getProfile(userId).teams();
        List<UUID> teamIds = teams.stream().map(TeamSummary::teamId).toList();

        Map<UUID, String> teamNameById = teams.stream()
            .collect(Collectors.toMap(TeamSummary::teamId, TeamSummary::name));
        Map<UUID, String> teamDomainById = teams.stream()
            .collect(Collectors.toMap(TeamSummary::teamId, TeamSummary::domain));

        List<Sprint> activeSprints = sprintRepository.findActiveByTeamIds(teamIds);
        List<ActiveSprintSummary> activeSprintSummaries = activeSprints.stream()
            .map(sprint -> new ActiveSprintSummary(
                sprint.getId(),
                sprint.getTeamId(),
                teamNameById.getOrDefault(sprint.getTeamId(), "Unknown"),
                teamDomainById.getOrDefault(sprint.getTeamId(), ""),
                sprint.getGoal(),
                sprint.getStatus().name(),
                sprint.getPlannedAt(),
                sprint.getUserStories().size(),
                sprint.getUserStories().stream().mapToInt(UserStory::getStoryPoints).sum()
            ))
            .toList();

        List<Sprint> recentCompleted = sprintRepository.findRecentCompletedByTeamIds(teamIds, 5);
        List<RecentSprintSummary> recentSprintSummaries = recentCompleted.stream()
            .map(sprint -> {
                SprintReview review = sprint.getSprintReview();
                return new RecentSprintSummary(
                    sprint.getId(),
                    sprint.getTeamId(),
                    teamNameById.getOrDefault(sprint.getTeamId(), "Unknown"),
                    sprint.getGoal(),
                    review != null ? review.getGoalResult().name() : null,
                    review != null ? review.getTotalPlannedPts() : 0,
                    review != null ? review.getTotalCompletedPts() : 0,
                    review != null ? review.getSubmittedAt() : null
                );
            })
            .toList();

        return ResponseEntity.ok(new DashboardResponse(activeSprintSummaries, teams, recentSprintSummaries));
    }
}
