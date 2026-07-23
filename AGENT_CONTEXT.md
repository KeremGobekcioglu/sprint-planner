# Sprint Planner — Agent Context File

Read this file at the start of every session. It is the single source of truth for architecture decisions, conventions, and current feature status.

---

## 1. Project Overview

Sprint Planner is a web-based AI-assisted sprint planning tool for small software teams (5–15 people).
Users create teams, provide epics (text or UI inputs), and the system returns a sprint plan composed of user stories and tasks, with Fibonacci story-point estimates and developer assignments constrained by real capacity. Plans are reviewed in the UI and can be pushed to Trello.

Current stack and key infra:
- Java 21
- Spring Boot 3.5.14 (parent in pom.xml)
- Maven build
- PostgreSQL (runtime), H2 for tests
- React + Vite + TypeScript frontend (in `frontend/`)
- Flyway for DB migrations

AI and integrations:
- AI backend: Google GenAI / Gemini adapters are implemented (production adapters present) with stub fallbacks for offline/dev runs.
- Trello integration: `TrelloApiClient` and `TrelloPlanPublisher` are implemented and wired into the review/approve flow.
- Authentication: JWT-based authentication implemented (register/login, Spring Security wiring).

Memory system status:
- Memory is stored on disk under the configured `sprintplanner.memory.base-path` (default `memory/<team-id>/team_profile.md` and `.../sprint_history.md`). `PreferenceService` and `ReferenceBuilder` are implemented and actively generate/refresh these files. Continuous-mode detection is live in the orchestrator.

---

## 2. Architecture — Clean Monolith (Hexagonal)

The codebase is a single deployable monolith organized by clean/hexagonal boundaries. The rule to follow is:

> Dependencies only point inward: web → application → domain ← infrastructure

Domain classes do not import Spring framework types.

### Package structure (high-level)

```
com.sprintplanner
├── domain            # Entities, DTOs, pure domain services, port interfaces
├── application       # Use-case orchestration and application services (@Service)
├── infrastructure    # Adapters implementing domain ports (@Component/@Repository)
└── web               # HTTP controllers, request/response DTOs
```

### Notable packages and classes (current)

domain/
- model/: `Team`, `Developer`, `Sprint`, `UserStory`, `Task`, `SprintReview`, `DevCompletion`, `EditLog`, `AppUser`, (all JPA entities)
- dto/: `PlanningRequest`, `SprintPlan`, `UserStoryDto`, `TaskDto`, `Epic`, `SprintPlanMapper`
- port/in/: `PlanningInputPort`
- port/out/: `BreakdownPort`, `EstimationPort`, `CapacityPort`, `DistributionPort`, `SprintRepository`, `TeamRepository`, `DeveloperRepository`, `MemoryReader`, `MemoryWriter`, `PlanPublisher`, `EditLogRepository`, `ProgressPort`
- service/: `CapacityCalculator` (pure domain algorithm)

application/
- `PlanningOrchestrator` — primary use-case entrypoint implementing `PlanningInputPort`
- `PreferenceService` — builds `team_profile.md` from `EditLog`
- `ReferenceBuilder` — builds `sprint_history.md` from recent `SprintReview`s
- `SprintReviewService` — sprint close flow
- `ProfileService`, `TeamService`, plus a few helpers and seeders

infrastructure/
- ai/: `GeminiBreakdownAdapter`, `GeminiEstimationAdapter`, `GeminiDistributionAdapter` + `Stub*` fallbacks, `GeminiClientProvider`
- auth/: `JwtConfig`, `JwtService`/`JwtServiceImpl`, `JwtAuthFilter`, `SecurityConfig`
- persistence/: Spring Data Jpa repositories and small adapters (`JpaSprintRepository`, `JpaTeamRepository`, `JpaDeveloperRepository`, `TaskJpaRepository`, `SprintReviewJpaRepository`, `DevCompletionJpaRepository`, `EditLogJpaRepository`, `JpaUserRepository`)
- memory/: `MarkdownFileMemoryAdapter` (reads/writes memory files under configured base path)
- external/: `TrelloApiClient`, `TrelloList` helper
- publisher/: `TrelloPlanPublisher`, `ConsolePlanPublisher`
- capacity/: `CapacityCalculatorAdapter` (adapter to domain capacity calculator)
- progress/: `PlanningProgressStore` (pushes planning progress events)

web/
- controllers: `SprintPlannerController`, `TeamController`, `ReviewController`, `ProfileController`, `DashboardController`, `web.auth.AuthController`
- dto (http): request/response DTOs used by controllers
- util: `SecurityUtils` (extracts current userId / role from SecurityContext)

---

## 3. Dependency Injection Rules

- Spring DI wires infrastructure adapters into domain ports at startup.
- Application services and controllers must use constructor injection only — do not use field injection.
- Infrastructure adapters are annotated with `@Component` or `@Repository`. Application services with `@Service`. Controllers with `@RestController`.
- Note: `SecurityUtils` is used widely in controllers to extract the authenticated user id and role from the SecurityContext — treat it as the canonical utility for auth context in controllers.

---

## 4. Input Entry Points

All HTTP entry points that trigger planning produce a `PlanningRequest` and pass it to `PlanningOrchestrator.planSprint(PlanningRequest)` (via the `PlanningInputPort`).

Current `PlanningRequest` signature (source of truth):

```java
public record PlanningRequest(
    UUID teamId,
    UUID userId,                // current authenticated user — controllers set this from SecurityUtils
    List<Epic> epics,
    String domain,              // E_COMMERCE | FINTECH | GENERIC
    String sprintGoal,
    Map<UUID, Integer> daysOff  // developerId -> days off this sprint
) {}
```

Notes:
- `userId` is included and set by the controllers (do not remove it).
- There is no `previousSprintContext` field in the current `PlanningRequest` — continuous mode is detected by reading memory via `MemoryReader` inside the orchestrator.

Entry points:
- `POST /api/sprints/plan` — `SprintPlannerController.planSprint(PlanSprintHttpRequest)` converts to `PlanningRequest` and calls orchestrator.
- CLI: `CliRunner` converts input file to `PlanningRequest` and uses `PlanPublisher`.

---

## 5. Output Entry Points

Primary output port: `PlanPublisher`. Adapters:

- `TrelloPlanPublisher` — production publisher which creates lists/cards on a Trello board and writes `trello_card_id` back to tasks.
- `ConsolePlanPublisher` — prints plan JSON to stdout / CLI.
- Controllers return `SprintPlan` (DTOs) as REST responses for UI consumption.

The Trello publisher is wired into the review/approve flow (`POST /api/sprints/{sprintId}/trello-push` and the review approval flow calls `preferenceService.updateTeamProfile(...)`).

---

## 6. The Four Agents

The planning pipeline is orchestrated by `PlanningOrchestrator` in the application layer and calls ports in this order:

1) Breakdown (LLM): `BreakdownPort` — implemented by `GeminiBreakdownAdapter` (real) with `StubBreakdownAdapter` fallback. Inputs: epics, team profile, sprint history, totalCapacity. Output: list of `UserStory` with `Task`s.

2) Estimation (LLM): `EstimationPort` — implemented by `GeminiEstimationAdapter` (real) with `StubEstimationAdapter` fallback. Inputs: tasks + sprint history. Output: story/task `storyPoints` (Fibonacci set validated by domain).

3) Capacity (pure domain): `CapacityPort` / `CapacityCalculator` — deterministic algorithm (no LLM) computing per-developer available points from `baseVelocity`, days off, and recent completion rates.

4) Distribution (LLM): `DistributionPort` — implemented by `GeminiDistributionAdapter` (real) with `StubDistributionAdapter` fallback. Inputs: estimated tasks, capacity map, developer capabilities, team profile, sprint history. Output: `SprintPlan` with assigned developers.

Notes:
- All three Gemini adapters are implemented and wired; stubs remain available for offline development and tests and are registered as fallbacks.
- All three adapters now use SDK structured output: `GeminiClientProvider.generate(prompt, temperature, model, Schema)` sets `responseMimeType("application/json")` and `responseSchema(schema)` on the config. The existing 3-arg overload is unchanged (used for guidance/retrospective). Fence-stripping is kept as a defensive fallback in all three adapters.
- Distribution uses capability-based routing: developer `capabilities` are matched to task `role_tag` (capabilities stored as CSV string in `Developer.capabilities`, parsed via `Developer.getCapabilities()` transient getter).
- Capacity enforcement happens after estimation: the distribution adapter must respect capacities computed by `CapacityCalculator`.
- `GeminiBreakdownAdapter.isValid()` requires ≥1 task per story (not ≥2); single-role epics (e.g. backend-only) are valid. Retry re-sends the full original prompt. Epic-title fallback is deterministic via `LinkedHashSet`. Capacity prompt block no longer contradicts itself.
- `GeminiEstimationAdapter` retries once before falling back to heuristics; logs WARN by task ID when the model omits a task; variety-check bias removed.
- Do not change `GeminiClientProvider.generate(String, float, String)` signature — it is called by `SprintPlannerController` (task guidance) and `RetrospectiveService` which need free-text responses.

---

## 7. Memory System

Current implementation:
- Memory is stored on disk under the configured base path (property `sprintplanner.memory.base-path`, default `memory`). Files per team:
  - `memory/<teamId>/team_profile.md` — preferences written by `PreferenceService` after sprint approval.
  - `memory/<teamId>/sprint_history.md` — generated/updated by `ReferenceBuilder` after sprint close; keeps a rolling 3-sprint window.

Behavior and wiring:
- `PreferenceService.updateTeamProfile(teamId, editLogs)` is implemented and writes `team_profile.md`.
- `ReferenceBuilder.updateSprintHistory(teamId, reviews)` is implemented and writes `sprint_history.md`.
- `PlanningOrchestrator` detects continuous mode (if `sprintRepository.countCompletedByTeamId(teamId) > 0`) and reads memory via `MemoryReader` before building prompts.
- Memory is injected into LLM prompts as plain text.

Note: A DB-backed `team_memory` table was considered but not applied — memory remains on the filesystem in this build.

---

## 8. Data Models (Database)

### Entity relationships (current)

```
Team ──< Developer
Team ──< Sprint
Sprint ──< UserStory
UserStory ──< Task
Developer ──< Task (assigned_dev_id)
Sprint ──| SprintReview
SprintReview ──< DevCompletion
Sprint ──< EditLog
```

### Field reference (actual fields present in code and migrations)

Team (src/main/java/com/sprintplanner/domain/model/Team.java)
- id UUID PK
- name
- domain (E_COMMERCE/FINTECH/GENERIC)
- sprint_length_days
- owner_id UUID (FK → app_user.id) — team ownership/ACL
- trello_board_id
- created_at

Developer (src/main/java/com/sprintplanner/domain/model/Developer.java)
- id UUID PK
- team_id FK
- name
- role (freeform role label)
- capabilities TEXT — comma-separated CSV stored in DB; parsed by a @Transient getter returning List<String>
- base_velocity (int)
- trello_member_id VARCHAR — optional mapping to Trello member

Sprint (src/main/java/com/sprintplanner/domain/model/Sprint.java)
- id UUID PK
- team_id FK
- sprint_number (int)
- goal
- domain_override
- status (PLANNING/IN_REVIEW/ACTIVE/COMPLETED)
- planned_at, reviewed_at
- @OrderColumn(name = "story_order") on `userStories` for deterministic ordering

UserStory (src/main/java/com/sprintplanner/domain/model/UserStory.java)
- id UUID PK
- sprint_id FK
- epic_title
- title
- story_points
- status
- @OrderColumn(name = "task_order") on `tasks`

Task (src/main/java/com/sprintplanner/domain/model/Task.java)
- id UUID PK
- story_id FK
- assigned_dev_id FK (nullable)
- title, description, acceptance_criterion
- role_tag (used for capability matching)
- story_points
- status
- trello_card_id

SprintReview, DevCompletion, EditLog and AppUser are present and their fields match the Flyway migrations in `src/main/resources/db/migration`.

Important: `team_profile.md` and `sprint_history.md` are NOT stored in the database in the current implementation — they live on the configured filesystem path.

---

## 9. Dependencies (pom.xml highlights)

Key dependencies present in `pom.xml`:
- Spring Boot parent 3.5.14 (project built for Spring Boot 3.5.x)
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-validation
- spring-boot-starter-actuator
- spring-boot-starter-security
- flyway-core, flyway-database-postgresql
- postgresql driver
- h2 (runtime test support)
- spring-dotenv (env file support)
- spring-boot-devtools
- testcontainers + test support
- Google GenAI client: `com.google.genai:google-genai:1.0.0` (used by Gemini adapters)
- JWT libraries: `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson` (0.12.6)

Java version: 21 (project property `java.version`)

---

## 10. Current Feature Status

| Feature                          | Status |
|----------------------------------|--------|
| Auth (JWT login/register)       | Done   |
| Team ownership + ADMIN/USER     | Done   |
| Developer capabilities          | Done   |
| AI pipeline (all 3 agents)      | Done   |
| Capacity enforcement            | Done   |
| Sprint persistence              | Done   |
| Review screen + drag/drop       | Done   |
| Edit logging                    | Done   |
| Sprint close flow               | Done   |
| Memory system (pref + history)  | Done   |
| Continuous mode detection       | Done   |
| Profile page                    | Done   |
| Dashboard                       | Done   |
| Trello push                     | Done   |
| Trello member mapping           | Done (DB column present; card-member assignment not automated) |
| Trello OAuth per user           | Not started |
| Delete sprint                   | Not started |
| Previous sprint import          | Not started |
| CSV/Excel epic upload           | Not started |
| Admin cleanup endpoints         | Not started |

Notes:
- Several features are functionally present in the backend and the frontend UI (auth, planning, review, memory). Some Trello UX bits (per-user OAuth and assigning Trello card members) remain to be implemented.

---

## 11. Agent Prompt Rules (for the Developer)

Keep the existing rules and add these project-specific constraints:

1. Always read `PROJECT_MAP.md` before starting any non-trivial task.
2. Check existing Flyway migration files in `src/main/resources/db/migration` before creating new migrations — never create a migration for a column that already exists.
3. `PlanningRequest` has `userId` as the second field — do not remove it or change its position without deliberate migration of callers.
4. Do not touch `GeminiBreakdownAdapter`, `GeminiEstimationAdapter`, or `GeminiDistributionAdapter` prompt text unless the task explicitly requires it — these prompts are sensitive and tuned.
5. Do not change the `PlanningOrchestrator` constructor signature or its DI wiring — many components depend on that constructor injection order.
6. Constructor injection only — if code with field injection is produced, re-prompt the generator.

---

## 12. Key Design Decisions (Rationale)

Retain the existing rationale and add the following confirmed decisions based on current implementation:

- Trello is linked per-team (Team.trello_board_id) and is configured via server-side API key/token. Per-user Trello OAuth is planned later.
- Developer capabilities are stored as a CSV string in `Developer.capabilities` column and parsed by a `@Transient` getter returning a List<String>.
- `SprintPlan` and controller responses use lightweight DTOs (`UserStoryDto`, `TaskDto`) to avoid circular JSON serialization and keep responses small.
- Deterministic ordering is enforced using `@OrderColumn` on `Sprint.userStories` (`story_order`) and `UserStory.tasks` (`task_order`).
- `spring.jpa.open-in-view=false` is set — all lazy loading must be explicit; controllers and mappers use explicit fetch strategies or repository methods that load necessary associations.

---

## 13. Test Scenarios (for validation)

Existing scenarios (A–D) remain; add the following Phase-2 specific tests:

E — Continuous mode
- Team with 2+ completed sprints. Run planner and verify that `team_profile.md` and `sprint_history.md` are loaded and that the generated prompts / plan differ from a fresh-start baseline.

F — Trello push
- Link a Trello board to a team, generate + approve a plan and push to Trello. Verify lists and cards are created and `task.trello_card_id` is populated in DB.

G — Sprint import / previous sprint context
- For a fresh team, paste or supply previous sprint context in the UI (if implemented) and verify that the breakdown logs contain references to the previous sprint. (Note: import endpoint not yet implemented.)

H — Admin reset
- Call `DELETE /api/admin/reset` (if implemented) or an equivalent admin cleanup endpoint and verify all sprint/plan data is cleared while teams and user accounts remain. (Admin reset endpoint currently not implemented.)

---

## 14. Environment Variables Required

Set the following environment variables in your deployment or local `.env` when running the service:

- JWT_SECRET=<64 char random string> (used by JwtService)
- GEMINI_API_KEY=<Google AI Studio / GenAI key>
- TRELLO_API_KEY=<from trello.com>
- TRELLO_TOKEN=<from trello.com/1/authorize> (server-side token for the app)
- SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sprintplanner
- SPRING_DATASOURCE_USERNAME=postgres
- SPRING_DATASOURCE_PASSWORD=postgres

Note: `application.properties` provides development defaults; always use secure secrets for production.

---

## 15. Current Known Issues / TODOs

Be specific when modifying these areas.

- Trello card member assignment: developers have `trello_member_id` but `TrelloPlanPublisher` does not attach members to created cards. File: `infrastructure/publisher/TrelloPlanPublisher.java` — enhancement needed to map `Developer.trello_member_id` to Trello card members when creating cards.
- Trello OAuth per user: not implemented. If you need per-user boards, add OAuth flow and store per-user tokens carefully.
- Sprint number assignment: `SprintPlannerController.planSprint()` currently sets `sprintNumber` to `1` as a placeholder; should be replaced with a query for the team's max sprint number + 1. File: `web/SprintPlannerController.java` lines ~88–96.
- Delete sprint endpoint is not implemented (no `DELETE /api/sprints/{sprintId}`).
- Previous sprint import endpoint is not implemented — there is no API to import a prior sprint into the planner; UI-side import helpers exist only in mock functions.
- CSV/Excel epic upload UI is not implemented — frontend has no `EpicFileUpload` component; planned but not added.
- Admin cleanup endpoints (e.g. `/api/admin/reset`) are not implemented.
- Memory storage: files live on disk under `memory/` — if you switch to DB-backed memory, add a Flyway migration and `JpaTeamMemoryRepository` before removing `MarkdownFileMemoryAdapter`.

If you add migrations, always inspect `src/main/resources/db/migration` first — do not duplicate columns/migrations.

---

Do not edit `AGENT_CONTEXT_PHASE2.md` — it remains a historical reference for Phase 2 planning.

*Last updated: generated from codebase scan on 2026-05-24*
