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
        "developer",[INFO] Running com.sprintplanner.web.ReviewControllerIntegrationTest
00:08:40.188 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [com.sprintplanner.web.ReviewControllerIntegrationTest]: ReviewControllerIntegrationTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
00:08:40.312 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration com.sprintplanner.SprintPlannerApplication for test class com.sprintplanner.web.ReviewControllerIntegrationTest
00:08:40.430 [main] INFO org.springframework.boot.devtools.restart.RestartApplicationListener -- Restart disabled due to context in which it is running

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::               (v3.5.14)

2026-04-27T00:08:40.624+03:00  INFO 26204 --- [sprint-planner] [           main] c.s.web.ReviewControllerIntegrationTest  : Starting ReviewControllerIntegrationTest using Java 21.0.10 with PID 26204 (started by Kerem in C:\Users\Kerem\Desktop\sprint-planner)
2026-04-27T00:08:40.625+03:00  INFO 26204 --- [sprint-planner] [           main] c.s.web.ReviewControllerIntegrationTest  : No active profile set, falling back to 1 default profile: "default"
2026-04-27T00:08:41.050+03:00  INFO 26204 --- [sprint-planner] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Bootstrapping Spring Data JPA repositories in DEFAULT mode.
2026-04-27T00:08:41.082+03:00  INFO 26204 --- [sprint-planner] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Finished Spring Data repository scanning in 25 ms. Found 3 JPA repository interfaces.
2026-04-27T00:08:41.665+03:00  INFO 26204 --- [sprint-planner] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port 0 (http)
2026-04-27T00:08:41.674+03:00  INFO 26204 --- [sprint-planner] [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2026-04-27T00:08:41.674+03:00  INFO 26204 --- [sprint-planner] [           main] o.apache.catalina.core.StandardEngine    : Starting Servlet engine: [Apache Tomcat/10.1.54]
2026-04-27T00:08:41.716+03:00  INFO 26204 --- [sprint-planner] [           main] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring embedded WebApplicationContext
2026-04-27T00:08:41.717+03:00  INFO 26204 --- [sprint-planner] [           main] w.s.c.ServletWebServerApplicationContext : Root WebApplicationContext: initialization completed in 1063 ms
2026-04-27T00:08:41.968+03:00  INFO 26204 --- [sprint-planner] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
2026-04-27T00:08:42.108+03:00  INFO 26204 --- [sprint-planner] [           main] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@65259c53
2026-04-27T00:08:42.109+03:00  INFO 26204 --- [sprint-planner] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
2026-04-27T00:08:42.133+03:00  INFO 26204 --- [sprint-planner] [           main] org.flywaydb.core.FlywayExecutor         : Database: jdbc:postgresql://localhost:5433/mydatabase (PostgreSQL 18.3)
2026-04-27T00:08:42.144+03:00  WARN 26204 --- [sprint-planner] [           main] o.f.c.internal.database.base.Database    : Flyway upgrade recommended: PostgreSQL 18.3 is newer than this version of Flyway and support has not been tested. The latest supported version of PostgreSQL is 17.
2026-04-27T00:08:42.176+03:00  INFO 26204 --- [sprint-planner] [           main] o.f.core.internal.command.DbValidate     : Successfully validated 1 migration (execution time 00:00.022s)
2026-04-27T00:08:42.206+03:00  INFO 26204 --- [sprint-planner] [           main] o.f.core.internal.command.DbMigrate      : Current version of schema "public": 1
2026-04-27T00:08:42.209+03:00  INFO 26204 --- [sprint-planner] [           main] o.f.core.internal.command.DbMigrate      : Schema "public" is up to date. No migration necessary.
2026-04-27T00:08:42.292+03:00  INFO 26204 --- [sprint-planner] [           main] o.hibernate.jpa.internal.util.LogHelper  : HHH000204: Processing PersistenceUnitInfo [name: default]
2026-04-27T00:08:42.354+03:00  INFO 26204 --- [sprint-planner] [           main] org.hibernate.Version                    : HHH000412: Hibernate ORM core version 6.6.49.Final
2026-04-27T00:08:42.386+03:00  INFO 26204 --- [sprint-planner] [           main] o.h.c.internal.RegionFactoryInitiator    : HHH000026: Second-level cache disabled
2026-04-27T00:08:42.602+03:00  INFO 26204 --- [sprint-planner] [           main] o.s.o.j.p.SpringPersistenceUnitInfo      : No LoadTimeWeaver setup: ignoring JPA class transformer
2026-04-27T00:08:42.669+03:00  INFO 26204 --- [sprint-planner] [           main] org.hibernate.orm.connections.pooling    : HHH10001005: Database info:
        Database JDBC URL [Connecting through datasource 'HikariDataSource (HikariPool-1)']
        Database driver: undefined/unknown
        Database version: 18.3
        Autocommit mode: undefined/unknown
        Isolation level: undefined/unknown
        Minimum pool size: undefined/unknown
        Maximum pool size: undefined/unknown
2026-04-27T00:08:43.293+03:00  INFO 26204 --- [sprint-planner] [           main] o.h.e.t.j.p.i.JtaPlatformInitiator       : HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)
2026-04-27T00:08:43.316+03:00  INFO 26204 --- [sprint-planner] [           main] j.LocalContainerEntityManagerFactoryBean : Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-04-27T00:08:43.912+03:00  INFO 26204 --- [sprint-planner] [           main] o.s.b.a.e.web.EndpointLinksResolver      : Exposing 1 endpoint beneath base path '/actuator'
2026-04-27T00:08:44.050+03:00  INFO 26204 --- [sprint-planner] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 59910 (http) with context path '/'
2026-04-27T00:08:44.058+03:00  INFO 26204 --- [sprint-planner] [           main] c.s.web.ReviewControllerIntegrationTest  : Started ReviewControllerIntegrationTest in 3.636 seconds (process running for 4.301)
Mockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
WARNING: A Java agent has been loaded dynamically (C:\Users\Kerem\.m2\repository\net\bytebuddy\byte-buddy-agent\1.17.8\byte-buddy-agent-1.17.8.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
2026-04-27T00:08:44.417+03:00  INFO 26204 --- [sprint-planner] [o-auto-1-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring DispatcherServlet 'dispatcherServlet'
2026-04-27T00:08:44.417+03:00  INFO 26204 --- [sprint-planner] [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : Initializing Servlet 'dispatcherServlet'
2026-04-27T00:08:44.418+03:00  INFO 26204 --- [sprint-planner] [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : Completed initialization in 1 ms
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.404 s -- in com.sprintplanner.web.ReviewControllerIntegrationTest
[INFO] Running com.sprintplanner.web.SprintPlannerControllerIntegrationTest
2026-04-27T00:08:44.501+03:00  INFO 26204 --- [sprint-planner] [           main] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration classes for test class [com.sprintplanner.web.SprintPlannerControllerIntegrationTest]: SprintPlannerControllerIntegrationTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-04-27T00:08:44.506+03:00  INFO 26204 --- [sprint-planner] [           main] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.sprintplanner.SprintPlannerApplication for test class com.sprintplanner.web.SprintPlannerControllerIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.204 s -- in com.sprintplanner.web.SprintPlannerControllerIntegrationTest
[INFO] Running com.sprintplanner.web.TeamControllerIntegrationTest
2026-04-27T00:08:44.706+03:00  INFO 26204 --- [sprint-planner] [           main] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration classes for test class [com.sprintplanner.web.TeamControllerIntegrationTest]: TeamControllerIntegrationTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-04-27T00:08:44.710+03:00  INFO 26204 --- [sprint-planner] [           main] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.sprintplanner.SprintPlannerApplication for test class com.sprintplanner.web.TeamControllerIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.086 s -- in com.sprintplanner.web.TeamControllerIntegrationTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  8.019 s
[INFO] Finished at: 2026-04-27T00:08:45+03:00
[INFO] ------------------------------------------------------------------------
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
