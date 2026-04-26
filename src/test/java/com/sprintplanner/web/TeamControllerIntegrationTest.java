package com.sprintplanner.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sprintplanner.web.dto.AddDeveloperHttpRequest;
import com.sprintplanner.web.dto.CreateTeamHttpRequest;
import com.sprintplanner.web.dto.DeveloperHttpDto;
import com.sprintplanner.web.dto.TeamHttpDto;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeamControllerIntegrationTest {

    private static final String TEAM_TABLE = "team";
    private static final String DEVELOPER_TABLE = "developer";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE edit_log, dev_completion, sprint_review, task, user_story, sprint, developer, team CASCADE");
    }

    @Test
    void managesTeamsAndDevelopersOverHttpAndPersistsExpectedRows() {
        assertEquals(0, countRows(TEAM_TABLE));
        assertEquals(0, countRows(DEVELOPER_TABLE));

        ResponseEntity<TeamHttpDto> createTeamResponse = restTemplate.exchange(
            "http://localhost:" + port + "/api/teams",
            HttpMethod.POST,
            new HttpEntity<>(new CreateTeamHttpRequest("Step12.5 Team", "GENERIC", 10, null)),
            TeamHttpDto.class
        );

        TeamHttpDto createdTeam = createTeamResponse.getBody();
        assertNotNull(createdTeam);
        assertNotNull(createdTeam.id());
        assertEquals(1, countRows(TEAM_TABLE));

        ResponseEntity<TeamHttpDto> getTeamResponse = restTemplate.exchange(
            "http://localhost:" + port + "/api/teams/" + createdTeam.id(),
            HttpMethod.GET,
            HttpEntity.EMPTY,
            TeamHttpDto.class
        );

        TeamHttpDto fetchedTeam = getTeamResponse.getBody();
        assertNotNull(fetchedTeam);
        assertEquals(createdTeam.id(), fetchedTeam.id());
        assertEquals(1, countRows(TEAM_TABLE));

        ResponseEntity<TeamHttpDto[]> teamsResponse = restTemplate.exchange(
            "http://localhost:" + port + "/api/teams",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            TeamHttpDto[].class
        );

        TeamHttpDto[] teams = teamsResponse.getBody();
        assertNotNull(teams);
        assertFalse(Arrays.stream(teams).noneMatch(team -> team.id().equals(createdTeam.id())));
        assertEquals(1, countRows(TEAM_TABLE));

        ResponseEntity<DeveloperHttpDto> createDeveloperResponse = restTemplate.exchange(
            "http://localhost:" + port + "/api/teams/" + createdTeam.id() + "/developers",
            HttpMethod.POST,
            new HttpEntity<>(new AddDeveloperHttpRequest("Kerem", "backend", 20)),
            DeveloperHttpDto.class
        );

        DeveloperHttpDto createdDeveloper = createDeveloperResponse.getBody();
        assertNotNull(createdDeveloper);
        assertNotNull(createdDeveloper.id());
        assertEquals(1, countRows(DEVELOPER_TABLE));

        ResponseEntity<Void> deleteDeveloperResponse = restTemplate.exchange(
            "http://localhost:" + port + "/api/teams/" + createdTeam.id() + "/developers/" + createdDeveloper.id(),
            HttpMethod.DELETE,
            HttpEntity.EMPTY,
            Void.class
        );

        assertEquals(HttpStatus.NO_CONTENT, deleteDeveloperResponse.getStatusCode());
        assertEquals(0, countRows(DEVELOPER_TABLE));

        ResponseEntity<DeveloperHttpDto[]> developersResponse = restTemplate.exchange(
            "http://localhost:" + port + "/api/teams/" + createdTeam.id() + "/developers",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            DeveloperHttpDto[].class
        );

        DeveloperHttpDto[] developers = developersResponse.getBody();
        assertNotNull(developers);
        UUID deletedDeveloperId = createdDeveloper.id();
        assertFalse(Arrays.stream(developers).anyMatch(developer -> developer.id().equals(deletedDeveloperId)));
        assertEquals(0, countRows(DEVELOPER_TABLE));
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
