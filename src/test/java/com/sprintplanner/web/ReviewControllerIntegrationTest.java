package com.sprintplanner.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sprintplanner.web.dto.DevCompletionHttpDto;
import com.sprintplanner.web.dto.EditLogHttpDto;
import com.sprintplanner.web.dto.ReviewHttpDto;
import com.sprintplanner.web.dto.SubmitReviewHttpRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReviewControllerIntegrationTest {

    private static final String[] DOMAIN_TABLES = {
        "team",
        "developer",
        "sprint",
        "user_story",
        "task",
        "sprint_review",
        "dev_completion",
        "edit_log"
    };

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
    void processReviewWritesMemoryAndDoesNotMutateDatabaseTables() throws IOException {
        UUID teamId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        Path teamMemoryDir = Path.of("memory", teamId.toString());
        if (Files.exists(teamMemoryDir)) {
            deleteRecursively(teamMemoryDir);
        }

        ReviewHttpDto latestReview = new ReviewHttpDto(
            reviewId,
            sprintId,
            "YES",
            "No blockers",
            30,
            24,
            Instant.now(),
            List.of(
                new DevCompletionHttpDto(
                    UUID.randomUUID(),
                    reviewId,
                    UUID.randomUUID(),
                    10,
                    8
                )
            )
        );

        SubmitReviewHttpRequest request = new SubmitReviewHttpRequest(
            teamId,
            latestReview,
            List.of(latestReview),
            List.of(
                new EditLogHttpDto(
                    UUID.randomUUID(),
                    sprintId,
                    "story_points",
                    "5",
                    "3",
                    "backend",
                    Instant.now()
                )
            )
        );

        Map<String, Integer> beforeCounts = countAllTables();

        ResponseEntity<String> response = restTemplate.exchange(
            "http://localhost:" + port + "/api/reviews/process",
            HttpMethod.POST,
            new HttpEntity<>(request),
            String.class
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Review processed.", response.getBody());

        Map<String, Integer> afterCounts = countAllTables();
        for (String tableName : DOMAIN_TABLES) {
            assertEquals(
                beforeCounts.get(tableName),
                afterCounts.get(tableName),
                "Review endpoint unexpectedly mutated table: " + tableName
            );
        }

        Path teamProfilePath = teamMemoryDir.resolve("team_profile.md");
        Path sprintHistoryPath = teamMemoryDir.resolve("sprint_history.md");

        assertTrue(Files.exists(teamProfilePath));
        assertTrue(Files.exists(sprintHistoryPath));

        String teamProfileContent = Files.readString(teamProfilePath);
        String sprintHistoryContent = Files.readString(sprintHistoryPath);

        assertTrue(teamProfileContent.contains("Team Profile Preferences"));
        assertTrue(sprintHistoryContent.contains("Sprint History"));
    }

    private Map<String, Integer> countAllTables() {
        return Map.of(
            "team", countRows("team"),
            "developer", countRows("developer"),
            "sprint", countRows("sprint"),
            "user_story", countRows("user_story"),
            "task", countRows("task"),
            "sprint_review", countRows("sprint_review"),
            "dev_completion", countRows("dev_completion"),
            "edit_log", countRows("edit_log")
        );
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (var walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to clean memory path: " + current, e);
                }
            });
        }
    }
}
