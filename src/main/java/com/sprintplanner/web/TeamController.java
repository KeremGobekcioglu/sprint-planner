package com.sprintplanner.web;

import com.sprintplanner.application.TeamService;
import com.sprintplanner.domain.model.AppUser;
import com.sprintplanner.domain.model.Developer;
import com.sprintplanner.domain.model.Team;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.port.out.MemoryReader;
import com.sprintplanner.domain.port.out.MemoryWriter;
import com.sprintplanner.domain.port.out.TeamRepository;
import com.sprintplanner.infrastructure.external.TrelloApiClient;
import com.sprintplanner.infrastructure.external.TrelloList;
import com.sprintplanner.infrastructure.external.TrelloMemberDto;
import com.sprintplanner.infrastructure.persistence.JpaUserRepository;
import com.sprintplanner.web.dto.AddDeveloperHttpRequest;
import com.sprintplanner.web.dto.CreateTeamHttpRequest;
import com.sprintplanner.web.dto.DeveloperHttpDto;
import com.sprintplanner.web.dto.LinkTrelloMemberRequest;
import com.sprintplanner.web.dto.TeamHttpDto;
import com.sprintplanner.web.dto.TeamMemoryResponse;
import com.sprintplanner.web.dto.SeedSampleTeamsResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;
    private final TeamRepository teamRepository;
    private final DeveloperRepository developerRepository;
    private final MemoryReader memoryReader;
    private final MemoryWriter memoryWriter;
    private final TrelloApiClient trelloApiClient;
    private final JpaUserRepository userRepository;

    public TeamController(TeamService teamService, TeamRepository teamRepository,
            DeveloperRepository developerRepository, MemoryReader memoryReader,
            MemoryWriter memoryWriter, TrelloApiClient trelloApiClient, JpaUserRepository userRepository) {
        this.teamService = teamService;
        this.teamRepository = teamRepository;
        this.developerRepository = developerRepository;
        this.memoryReader = memoryReader;
        this.memoryWriter = memoryWriter;
        this.trelloApiClient = trelloApiClient;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<TeamHttpDto>> getTeams() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        List<TeamHttpDto> teams = teamService.getTeams(currentUserId, isAdmin).stream().map(TeamHttpDto::from).toList();
        return ResponseEntity.ok(teams);
    }

    @PostMapping
    public ResponseEntity<TeamHttpDto> createTeam(@RequestBody CreateTeamHttpRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
            TeamHttpDto.from(
                teamService.createTeam(
                    request.name(),
                    request.domain(),
                    request.sprintLengthDays(),
                    request.trelloBoardId(),
                    currentUserId
                )
            )
        );
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamHttpDto> getTeam(@PathVariable UUID teamId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        return ResponseEntity.ok(TeamHttpDto.from(teamService.getTeam(teamId, currentUserId, isAdmin)));
    }

    @GetMapping("/{teamId}/developers")
    public ResponseEntity<List<DeveloperHttpDto>> getDevelopers(@PathVariable UUID teamId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        List<DeveloperHttpDto> developers = teamService.getDevelopers(teamId, currentUserId, isAdmin)
            .stream()
            .map(DeveloperHttpDto::from)
            .toList();
        return ResponseEntity.ok(developers);
    }

    @PostMapping("/{teamId}/developers")
    public ResponseEntity<DeveloperHttpDto> addDeveloper(
        @PathVariable UUID teamId,
        @RequestBody AddDeveloperHttpRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        Developer developer = teamService.addDeveloper(
            teamId,
            currentUserId,
            isAdmin,
            request.name(),
            request.role(),
            request.capabilities(),
            request.baseVelocity()
        );
        return ResponseEntity.ok(DeveloperHttpDto.from(developer));
    }

    @DeleteMapping("/{teamId}/developers/{developerId}")
    public ResponseEntity<Void> removeDeveloper(@PathVariable UUID teamId, @PathVariable UUID developerId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        teamService.removeDeveloper(teamId, currentUserId, isAdmin, developerId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{teamId}/memory")
    public ResponseEntity<TeamMemoryResponse> getTeamMemory(@PathVariable UUID teamId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        var team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        if (!isAdmin && (team.getOwnerId() == null || !team.getOwnerId().equals(currentUserId))) {
            throw new IllegalArgumentException("Team not found: " + teamId);
        }

        String teamProfile = memoryReader.readTeamProfile(teamId);
        String sprintHistory = memoryReader.readSprintHistory(teamId);

        TeamMemoryResponse response = new TeamMemoryResponse(
            teamProfile.isEmpty() ? null : teamProfile,
            sprintHistory.isEmpty() ? null : sprintHistory,
            teamProfile.isEmpty() ? null : memoryReader.getTeamProfileLastUpdated(teamId),
            sprintHistory.isEmpty() ? null : memoryReader.getSprintHistoryLastUpdated(teamId)
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{teamId}/memory/profile")
    public ResponseEntity<Void> clearTeamProfile(@PathVariable UUID teamId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        var team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        if (!isAdmin && (team.getOwnerId() == null || !team.getOwnerId().equals(currentUserId))) {
            throw new IllegalArgumentException("Team not found: " + teamId);
        }

        memoryWriter.writeTeamProfile(teamId, "");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trello/status")
    public ResponseEntity<Map<String, Object>> getTrelloStatus() {
        com.sprintplanner.infrastructure.config.TrelloConfig cfg =
                trelloApiClient.getConfig();
        String key = cfg.getApiKey();
        String tok = cfg.getToken();
        boolean keySet = key != null && !key.isBlank();
        boolean tokenSet = tok != null && !tok.isBlank();
        return ResponseEntity.ok(Map.of(
                "apiKeySet", keySet,
                "tokenSet", tokenSet,
                "apiKeyPreview", keySet ? key.substring(0, Math.min(4, key.length())) + "***" : "(not set)",
                "tokenPreview", tokenSet ? tok.substring(0, Math.min(4, tok.length())) + "***" : "(not set)",
                "baseUrl", cfg.getBaseUrl(),
                "ready", keySet && tokenSet
        ));
    }

    @GetMapping("/trello/boards")
    public ResponseEntity<List<TrelloList>> getTrelloBoards() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        AppUser user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String userToken = user.getTrelloToken();
        if (userToken == null || userToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Connect your Trello account first. Go to Profile → Connect Trello.");
        }

        List<TrelloList> boards = trelloApiClient.getUserBoards(userToken);
        return ResponseEntity.ok(boards);
    }

    @PutMapping("/{teamId}/trello-board")
    public ResponseEntity<TeamHttpDto> linkTrelloBoard(
            @PathVariable UUID teamId,
            @RequestBody Map<String, String> body) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        if (!isAdmin && (team.getOwnerId() == null || !team.getOwnerId().equals(currentUserId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        team.setTrelloBoardId(body.get("boardId"));
        teamRepository.save(team);
        return ResponseEntity.ok(TeamHttpDto.from(team));
    }

    @GetMapping("/{teamId}/trello/members")
    public ResponseEntity<List<TrelloMemberDto>> getTrelloMembers(@PathVariable UUID teamId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        AppUser user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String userToken = user.getTrelloToken();
        if (userToken == null || userToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Connect your Trello account first. Go to Profile → Connect Trello.");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        if (!isAdmin && (team.getOwnerId() == null || !team.getOwnerId().equals(currentUserId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        if (team.getTrelloBoardId() == null || team.getTrelloBoardId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No Trello board linked to this team");
        }

        List<TrelloMemberDto> members = trelloApiClient.getBoardMembers(team.getTrelloBoardId(), userToken);
        return ResponseEntity.ok(members);
    }

    @PutMapping("/{teamId}/developers/{devId}/trello-member")
    public ResponseEntity<DeveloperHttpDto> linkTrelloMember(
            @PathVariable UUID teamId,
            @PathVariable UUID devId,
            @RequestBody LinkTrelloMemberRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        if (!isAdmin && (team.getOwnerId() == null || !team.getOwnerId().equals(currentUserId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        Developer developer = developerRepository.findById(devId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Developer not found"));

        if (!developer.getTeamId().equals(teamId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Developer does not belong to this team");
        }

        developer.setTrelloMemberId(request.trelloMemberId());
        Developer saved = developerRepository.save(developer);
        return ResponseEntity.ok(DeveloperHttpDto.from(saved));
    }

    @PostMapping("/sample-data")
    public ResponseEntity<SeedSampleTeamsResponse> seedSampleTeams() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        Map<String, Object[]> sampleTeams = new LinkedHashMap<>();
        sampleTeams.put("PayFlow Team", new Object[] { "FINTECH", 10, "payflow-board" });
        sampleTeams.put("ShopHub Team", new Object[] { "E_COMMERCE", 14, "shophub-board" });

        Map<String, List<Object[]>> sampleDevelopers = new LinkedHashMap<>();
        sampleDevelopers.put("PayFlow Team", List.of(
            new Object[] { "Alex Chen", "Senior Backend Engineer", List.of("backend"), 13 },
            new Object[] { "Jordan López", "Backend Engineer", List.of("backend"), 8 },
            new Object[] { "Maya Patel", "Frontend Engineer", List.of("frontend"), 7 },
            new Object[] { "Sam Wilson", "QA Engineer", List.of("qa"), 5 },
            new Object[] { "Casey Kim", "DevOps Engineer", List.of("devops"), 6 }
        ));
        sampleDevelopers.put("ShopHub Team", List.of(
            new Object[] { "Ava Rodriguez", "Frontend Lead", List.of("frontend"), 9 },
            new Object[] { "Marcus Thompson", "Backend Engineer", List.of("backend"), 8 },
            new Object[] { "Priya Singh", "Senior Backend Engineer", List.of("backend"), 11 },
            new Object[] { "Liam OReilley", "QA Lead", List.of("qa"), 7 },
            new Object[] { "Sophie Bergström", "Frontend Engineer", List.of("frontend"), 6 }
        ));

        int teamsCreated = 0;
        int developersCreated = 0;
        UUID firstTeamId = null;

        for (Map.Entry<String, Object[]> entry : sampleTeams.entrySet()) {
            String teamName = entry.getKey();
            Object[] values = entry.getValue();
            String domain = (String) values[0];
            int sprintLengthDays = (Integer) values[1];
            String trelloBoardId = (String) values[2];

            var existingTeam = teamRepository.findAll().stream()
                .filter(team -> teamName.equals(team.getName()))
                .findFirst()
                .orElse(null);

            var team = existingTeam != null
                ? existingTeam
                : teamService.createTeam(teamName, domain, sprintLengthDays, trelloBoardId, currentUserId);

            if (existingTeam == null) {
                teamsCreated++;
            }

            if (firstTeamId == null) {
                firstTeamId = team.getId();
            }

            var existingDeveloperNames = teamService.getDevelopers(team.getId(), currentUserId, isAdmin).stream()
                .map(developer -> developer.getName())
                .collect(java.util.stream.Collectors.toSet());

            for (Object[] developerValues : sampleDevelopers.getOrDefault(teamName, List.of())) {
                String developerName = (String) developerValues[0];
                if (existingDeveloperNames.contains(developerName)) {
                    continue;
                }
                String role = (String) developerValues[1];
                @SuppressWarnings("unchecked")
                List<String> capabilities = (List<String>) developerValues[2];
                int baseVelocity = (Integer) developerValues[3];
                teamService.addDeveloper(team.getId(), currentUserId, isAdmin, developerName, role, capabilities, baseVelocity);
                developersCreated++;
            }
        }

        return ResponseEntity.ok(new SeedSampleTeamsResponse(teamsCreated, developersCreated, firstTeamId == null ? null : firstTeamId.toString()));
    }
}
