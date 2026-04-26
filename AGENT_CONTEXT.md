# Sprint Planner — Agent Context File

> Read this file at the start of every session before generating any code.
> This is the single source of truth for architecture decisions, conventions, and build order.

---

## 1. Project Overview

A web-based AI sprint planning assistant for small software teams (5–15 people).
The user configures a team, pastes epics, and receives a full sprint plan broken into
user stories and tasks, estimated in Fibonacci story points, and assigned to developers
within real capacity limits. Plans are reviewed before being pushed to Trello.

**Stack:** Java 21 · Spring Boot 3.5.14 · Maven · PostgreSQL · React (separate repo)
**AI backend:** Gemini Flash (primary) · Ollama (dev fallback)
**External integrations:** Trello REST API

---

## 2. Architecture — Clean Monolith (Hexagonal)

The project is a **monolith now, designed to split into microservices later**.
Clean architecture is enforced via package structure. The single rule that must never be broken:

> **Dependencies only point inward: web → application → domain ← infrastructure**
> The domain layer must never import Spring, JPA, Gemini, or any framework class.

### Package structure

```
com.sprintplanner
├── domain            # Pure Java. Zero Spring. Zero JPA. Entities, port interfaces, domain services.
├── application       # Use-case orchestration. @Service only. Depends only on domain interfaces.
├── infrastructure    # Adapters implementing domain ports. @Repository, @Component, Spring JPA.
└── web               # @RestController, DTOs, input validation. Calls application layer only.
```

### Sub-packages

```
domain/
  model/              # Team, Developer, Sprint, UserStory, Task, SprintReview, DevCompletion, EditLog
  port/
    in/               # PlanningInputPort (use-case interfaces called by web/cli)
    out/              # BreakdownPort, EstimationPort, CapacityPort, DistributionPort,
                      # SprintRepository, TeamRepository, MemoryReader, MemoryWriter, PlanPublisher
  service/            # CapacityCalculator (pure algorithm, no LLM)
  dto/                # PlanningRequest, SprintPlan (shared input/output objects)

application/
  PlanningOrchestrator
  PreferenceService
  ReferenceBuilder
  SprintReviewService

infrastructure/
  ai/                 # GeminiBreakdownAdapter, GeminiEstimationAdapter, GeminiDistributionAdapter
  persistence/        # JpaSprintRepository, JpaTeamRepository, JpaDeveloperRepository
  memory/             # MarkdownFileMemoryAdapter (reads/writes team_profile.md, sprint_history.md)
  external/           # TrelloApiClient, OllamaFallbackClient
  publisher/          # TrelloPlanPublisher, ConsolePlanPublisher

web/
  SprintPlannerController
  TeamController
  ReviewController
  cli/                # CliRunner (CommandLineRunner for file/arg input)
```

---

## 3. Dependency Injection Rules

- Spring DI wires infrastructure adapters into domain ports at startup.
- Application layer classes receive port interfaces via **constructor injection only** — never field injection (`@Autowired` on fields is forbidden).
- To swap Gemini for Ollama: change which `@Primary` bean is active in `application.properties` — no domain or application code changes.
- Infrastructure adapters are `@Component` or `@Repository`. Application services are `@Service`. Controllers are `@RestController`. Nothing else gets Spring annotations.

---

## 4. Input Entry Points

All entry points produce one object: `PlanningRequest`. The orchestrator always receives this — it never knows which entry point was used.

| Entry point | How it works |
|---|---|
| React UI | `@RestController` maps HTTP POST body → `PlanningRequest` |
| CLI | `CommandLineRunner` reads `--file=epics.json` or inline args → `PlanningRequest` |
| File input | CLI adapter reads `.json` / `.md` file from disk → `PlanningRequest` |
| In-app string | `TestFixture` class constructs `PlanningRequest` directly — used in tests and demos |

```java
// PlanningRequest — the single seam between all entry points and the system
public record PlanningRequest(
    UUID teamId,
    List<Epic> epics,
    String domain,           // E_COMMERCE | FINTECH | GENERIC
    String sprintGoal,
    Map<UUID, Integer> daysOff  // developerId -> days off this sprint
) {}
```

---

## 5. Output Entry Points

`PlanPublisher` is the output port. Spring DI picks the right adapter at startup.

| Publisher | When used |
|---|---|
| `TrelloPlanPublisher` | Production — pushes cards to Trello board |
| `ConsolePlanPublisher` | CLI and test mode — prints JSON to stdout |
| REST response | Controller returns `SprintPlan` as HTTP response body |

---

## 6. The Four Agents

Each agent has a single responsibility. `PlanningOrchestrator` calls them in sequence.
If any agent fails, the pipeline halts and returns a structured error — no silent failures.

| Agent | LLM call? | Input | Output |
|---|---|---|---|
| `BreakdownAgent` | Yes | Epics + domain + preferences + sprint history | List of `UserStory` with child `Task` objects |
| `EstimationAgent` | Yes | Task list + velocity history | Same tasks with `story_points` added (Fibonacci: 1,2,3,5,8,13) |
| `CapacityAgent` | **No** | Developer list + sprint days + days off + history | `Map<UUID, Integer>` — developer → available points |
| `DistributionAgent` | Yes | Estimated tasks + capacity map + preferences | Final plan with `assigned_developer` per task |

`CapacityAgent` is a **pure domain service** (`CapacityCalculator`). It never calls an LLM.
Fresh start capacity factor: **0.80**. Continuous mode: uses actual `avg_completion_rate` from history.

---

## 7. Memory System

Two memory files per team. Stored on disk (or object storage). **Not in the database.**

| File | Type | Written by | Read by |
|---|---|---|---|
| `team_profile.md` | Preference memory (intent) | `PreferenceService` after Review Screen | Breakdown, Estimation, Distribution agents |
| `sprint_history.md` | Reference memory (outcomes) | `ReferenceBuilder` after Sprint Review | All four agents |

- `sprint_history.md` keeps a **rolling 3-sprint window**. Older data is archived but not injected into prompts.
- `EditLog` table is the DB source of truth — `PreferenceService` reads it to regenerate `team_profile.md`.
- Memory files are injected as plain text into agent prompts — no special format needed.

---

## 8. Data Models (Database)

### Entities and relationships

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

### Field reference

**Team**
```
id UUID PK | name | domain (E_COMMERCE/FINTECH/GENERIC) | sprint_length_days | trello_board_id | created_at
```

**Developer**
```
id UUID PK | team_id FK | name | role (backend/frontend/qa/devops) | base_velocity
```

**Sprint**
```
id UUID PK | team_id FK | sprint_number | goal | domain_override | status (PLANNING/IN_REVIEW/ACTIVE/COMPLETED) | planned_at | reviewed_at
```

**UserStory**
```
id UUID PK | sprint_id FK | epic_title | title | story_points | status
```

**Task**
```
id UUID PK | story_id FK | assigned_dev_id FK | title | description | acceptance_criterion | role_tag | story_points | status | trello_card_id
```

**SprintReview**
```
id UUID PK | sprint_id FK | goal_result (YES/PARTIALLY/NO) | blocker_note | total_planned_pts | total_completed_pts | submitted_at
```

**DevCompletion**
```
id UUID PK | review_id FK | developer_id FK | planned_pts | completed_pts
```

**EditLog**
```
id UUID PK | sprint_id FK | field | original_value | new_value | task_type | logged_at
```

### What is NOT in the database
- `team_profile.md` — filesystem
- `sprint_history.md` — filesystem
- Agent prompt templates — hardcoded in adapter classes or config files

---

## 9. Spring Initializr Dependencies (Already Selected)

| Dependency | Purpose |
|---|---|
| Spring Web | REST controllers, WebClient for Trello/Ollama HTTP calls |
| Validation | `@Valid` on request DTOs |
| Spring Data JPA | Repository adapters, `@Entity` |
| PostgreSQL Driver | Production DB |
| H2 Database | In-memory DB for tests — no Postgres needed locally for unit tests |
| Spring Boot DevTools | Hot reload |
| Spring Configuration Processor | `@ConfigurationProperties` autocomplete |
| Docker Compose Support | Auto-starts Postgres via docker-compose when running locally |
| Flyway Migration | Versioned SQL schema in `src/main/resources/db/migration/` |
| Spring Boot Actuator | `/health` and `/metrics` |
| Testcontainers | Real Postgres in integration tests |

### Add manually to pom.xml
```xml
<!-- Gemini SDK -->
<dependency>
  <groupId>com.google.cloud</groupId>
  <artifactId>google-cloud-vertexai</artifactId>
  <version>1.5.0</version>
</dependency>
```
Trello and Ollama use `WebClient` from Spring Web — no extra library needed.

---

## 10. Build Order — Follow This Exactly

Do not skip steps. Do not combine steps. One step = one agent prompt = one review.

```
STEP 01 — Domain entities (plain Java, no annotations)
          Team, Developer, Sprint, UserStory, Task,
          SprintReview, DevCompletion, EditLog

STEP 02 — Domain DTOs
          PlanningRequest, SprintPlan, Epic

STEP 03 — All port interfaces (domain/port/in and domain/port/out)
          PlanningInputPort
          BreakdownPort, EstimationPort, CapacityPort, DistributionPort
          SprintRepository, TeamRepository, DeveloperRepository
          MemoryReader, MemoryWriter
          PlanPublisher

STEP 04 — CapacityCalculator domain service (pure algorithm, no LLM)
          Unit test immediately — this is the easiest class to verify

STEP 05 — Flyway V1__init.sql
          Generate schema from entity field reference in Section 8

STEP 06 — JPA entity annotations on domain models
          @Entity, @Id, @Column, @ManyToOne, @OneToMany
          application.properties DB config

STEP 07 — STUB implementations of all agent ports
          StubBreakdownAdapter, StubEstimationAdapter, StubDistributionAdapter
          Return hardcoded realistic data — DO NOT call Gemini yet
          Register as @Primary beans

STEP 08 — JPA repository adapters (infrastructure/persistence)
          JpaSprintRepository, JpaTeamRepository, JpaDeveloperRepository

STEP 09 — MarkdownFileMemoryAdapter (infrastructure/memory)
          Reads/writes team_profile.md and sprint_history.md from disk

STEP 10 — PlanningOrchestrator (application layer)
          Calls agents in sequence using port interfaces
          Uses stubs from Step 07 — full pipeline test possible now

STEP 11 — PreferenceService + ReferenceBuilder + SprintReviewService

STEP 12 — REST controllers + DTOs (web layer)
          SprintPlannerController, TeamController, ReviewController
          Test full HTTP flow with stubs still active

STEP 13 — ConsolePlanPublisher + CliRunner
          Test CLI entry point with file input

STEP 14 — Real GeminiBreakdownAdapter (replace stub)
          Test in isolation before wiring into orchestrator

STEP 15 — Real GeminiEstimationAdapter (replace stub)

STEP 16 — Real GeminiDistributionAdapter (replace stub)

STEP 17 — TrelloApiClient + TrelloPlanPublisher

STEP 18 — OllamaFallbackClient + profile switching
          application-dev.properties activates Ollama adapters
```

---

## 11. Agent Prompt Rules (for the Developer)

When prompting your AI agent to generate code, always follow these rules:

1. **Always paste the relevant port interface** before asking for an implementation.
2. **Always paste the entity class** before asking for a repository or adapter.
3. **Never ask for more than one class per prompt** unless they are trivial data records.
4. **After every generated file: compile first, review logic second.** A clean compile catches 80% of AI mistakes.
5. **Stubs before real adapters.** Never wire Gemini before the full pipeline works with stubs.
6. **Constructor injection only.** If the agent generates `@Autowired` on a field, reject and re-prompt.
7. **Domain layer stays pure.** If the agent imports anything from `org.springframework` inside `com.sprintplanner.domain`, reject and re-prompt.
8. **One layer per session.** Don't jump between layers in the same prompt chain.

---

## 12. Key Design Decisions (Rationale)

| Decision | Reason |
|---|---|
| Monolith first | Simpler to build, debug, and deploy. Clean packages = extractable later. |
| Pure domain layer | Makes the core logic testable without Spring context. Speeds up unit tests. |
| Stubs before real AI | Validates architecture before adding Gemini complexity. |
| Files for memory | Markdown files are readable by humans and directly injectable into LLM prompts. |
| 3-sprint rolling window | Recent patterns matter more. Keeps prompt size bounded. |
| Constructor injection | Explicit dependencies, easier to test, no hidden coupling. |
| Fibonacci validation in domain | EstimationAgent output is always validated — no invalid point values reach the DB. |
| Properties over YAML | Simpler, no indentation bugs, easier to override via env vars in Docker. |

---

## 13. Test Scenarios (for validation)

| Scenario | Team | Domain | What it tests |
|---|---|---|---|
| A — E-Commerce | 6 devs (2 FE, 2 BE, 1 mobile, 1 QA) | E_COMMERCE | Normal happy path, mobile task routing |
| B — Fintech | 4 devs (3 BE, 1 QA) | FINTECH | Strict acceptance criteria, conservative estimates |
| C — Vague epics | 5 devs (mixed) | GENERIC | Graceful degradation on ambiguous input |

---

*Last updated: project setup phase — pre-implementation*
