# Sprint Planner — Phase 2 Agent Context File

> Read AGENT_CONTEXT.md and AGENT_CONTEXT_FRONTEND.md before this file.
> This file covers everything that was not implemented in Phase 1.
> Follow the build order exactly. One step = one agent prompt = one review.

---

## 1. What Phase 1 Delivered (Do Not Rebuild)

- ✅ Full AI pipeline: Breakdown → Estimation → Capacity → Distribution
- ✅ Team and developer CRUD
- ✅ Sprint plan persisted to DB
- ✅ Review screen with swim lanes, inline edit, edit logging
- ✅ Sprint close page (frontend skeleton)
- ✅ Fresh start flow (Flow A) — no memory, 0.80 capacity factor
- ✅ CLI entry point
- ✅ Console publisher

---

## 2. What Phase 2 Must Deliver

| Area | What is missing |
|---|---|
| Auth | No login/signup. No backend security. JWT interceptor is a placeholder. |
| Roles | No ADMIN/USER distinction. `GET /api/teams` returns ALL teams — security hole. |
| Team ownership | `Team` has no `owner_id`. No "my teams" concept exists. |
| Profile | No profile page. User cannot see their teams, stats, or what the system has learned. |
| Memory visibility | `team_profile.md` and `sprint_history.md` are invisible to the user. |
| Continuous mode | Existing team with sprint history does not load memory files (Flow B never triggers) |
| Memory system | `PreferenceService` and `ReferenceBuilder` are not implemented |
| Sprint close backend | `SprintReviewService` is not wired. Review form submits but nothing is saved. |
| Epic input formats | Only plain text supported. No Excel/CSV upload. |
| Trello | `TrelloApiClient` and `TrelloPlanPublisher` are not implemented |

---

## 3. Build Order — Phase 2

```
STEP 01 — Backend: Spring Security + JWT
STEP 02 — Backend: AppUser entity + role system (ADMIN/USER) + auth endpoints
STEP 03 — Backend: Team ownership — add owner_id FK, fix GET /api/teams to filter by user
STEP 04 — Backend: DB migrations V2 (users) + V3 (team owner_id) + V4 (sprint_review)
STEP 05 — Frontend: AuthStore + token + role persistence
STEP 06 — Frontend: LoginPage + SignupPage
STEP 07 — Frontend: ProtectedRoute — redirect to /login if not authenticated
STEP 08 — Backend: ProfileController — GET /api/profile, GET /api/profile/stats
STEP 09 — Backend: Memory visibility endpoints — GET /api/teams/:teamId/memory
STEP 10 — Frontend: ProfilePage — identity, teams, stats, memory viewer
STEP 11 — Backend: SprintReviewService (sprint close logic)
STEP 12 — Backend: ReferenceBuilder (writes sprint_history.md after review)
STEP 13 — Backend: PreferenceService (writes team_profile.md from EditLog)
STEP 14 — Backend: Continuous mode detection (Flow B trigger)
STEP 15 — Frontend: Epic input — CSV/Excel upload option
STEP 16 — Backend: TrelloApiClient + TrelloPlanPublisher
STEP 17 — Frontend: Trello push button on ReviewPage
```

---

## 4. Auth Design

### Decision: JWT, not Trello OAuth (for now)
Trello OAuth adds significant complexity and a hard dependency on the user having a Trello
account before they can use the app at all. JWT auth is self-contained and can be built
and tested immediately. Trello OAuth can be added later as an **additional** login option
without touching the JWT flow.

### Token flow
```
User submits login form
  → POST /api/auth/login { email, password }
  → Backend validates credentials, returns { token, userId, email }
  → Frontend stores token in localStorage
  → Axios interceptor attaches: Authorization: Bearer <token>
  → Spring Security filter validates token on every protected request
```

### What is protected
Every endpoint except:
- `POST /api/auth/login`
- `POST /api/auth/register`

### Backend stack additions
```xml
<!-- pom.xml -->
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.12.6</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### application.properties additions
```properties
jwt.secret=${JWT_SECRET}
jwt.expiration-ms=86400000
```
Add `JWT_SECRET` to your `.env` file — generate a random 64-char string.

---

## 5. Role System (Step 02)

### Two roles: ADMIN and USER

| Role | What they can see | What they can do |
|---|---|---|
| `USER` | Only teams they own | Create teams, plan sprints, close sprints |
| `ADMIN` | All teams, all sprints, all users | Everything USER can + delete any team/sprint, view all memory files |

### AppUser entity changes
```java
// domain/model/AppUser.java
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String passwordHash;
    @Column(nullable = false) private String role; // "ADMIN" | "USER"
    @Column(nullable = false) private LocalDateTime createdAt;
}
```

### How ADMIN is bootstrapped
First registered user automatically gets `ADMIN` role.
All subsequent registrations get `USER` role.
This lets you (the developer) register first, see everything, without a separate seed script.

### JWT claims — include role
```java
// JwtService — include role in token claims
claims.put("role", user.getRole());
claims.put("email", user.getEmail());
```

### Backend enforcement
- `GET /api/teams` — returns own teams for USER, all teams for ADMIN
- `GET /api/sprints` — scoped to team owner for USER, unrestricted for ADMIN
- `GET /api/profile/admin/users` — ADMIN only
- All other endpoints — ownership check: request's teamId must belong to authenticated user (skip check for ADMIN)

### Frontend enforcement
- Store role in `authStore`
- Show "Admin Panel" link in navbar only if `role === 'ADMIN'`
- Admin panel: list all users, all teams, all sprints — read only view

---

## 6. Team Ownership Fix (Step 03)

### DB change
```sql
-- V3__add_team_ownership.sql
ALTER TABLE team ADD COLUMN owner_id UUID REFERENCES app_user(id);
```

### Team entity change
```java
@Column(name = "owner_id", nullable = false)
private UUID ownerId;
```

### TeamController changes
- `POST /api/teams` — set `ownerId` from JWT token, not from request body
- `GET /api/teams` — filter by `ownerId = currentUserId` unless ADMIN
- `DELETE /api/teams/:id` — verify `ownerId == currentUserId` or ADMIN before deleting

### How to extract current user in controllers
```java
// Utility method — add to a SecurityUtils class in web layer
public static UUID getCurrentUserId() {
    return (UUID) SecurityContextHolder.getContext()
        .getAuthentication()
        .getPrincipal();
}
```
`JwtAuthFilter` sets the principal to the `UUID` extracted from the token.

---

## 7. Profile — Backend (Step 08)

### ProfileController endpoints
```
GET  /api/profile              → current user info + team list
GET  /api/profile/stats        → aggregate sprint stats for current user
GET  /api/teams/:teamId/memory → memory files for a specific team (owner or ADMIN only)
PUT  /api/profile              → update name (email change not supported for now)
```

### ProfileResponse shape
```java
public record ProfileResponse(
    UUID userId,
    String email,
    String name,
    String role,
    LocalDateTime createdAt,
    List<TeamSummary> teams   // teams owned by this user
)

public record TeamSummary(
    UUID teamId,
    String name,
    String domain,
    int developerCount,
    int completedSprints,
    LocalDateTime lastSprintAt  // null if no sprints yet
)
```

### ProfileStatsResponse shape
```java
public record ProfileStatsResponse(
    int totalSprintsPlanned,
    int totalSprintsCompleted,
    double averageVelocityAccuracy,  // avg(completedPts / plannedPts) across all sprints
    int goalHitRate,                  // % of sprints where goalResult = YES
    String mostActiveTeam            // team name with most completed sprints
)
```

### Memory visibility endpoint
```
GET /api/teams/:teamId/memory
Response:
{
  "teamProfile": "<contents of team_profile.md or null if not yet generated>",
  "sprintHistory": "<contents of sprint_history.md or null if not yet generated>",
  "teamProfileLastUpdated": "<ISO timestamp or null>",
  "sprintHistoryLastUpdated": "<ISO timestamp or null>"
}
```
Security: verify `team.ownerId == currentUserId` or `currentUser.role == ADMIN`.

---

## 8. Profile — Frontend (Step 10)

### New route
```
/profile  →  ProfilePage
```
Add link in navbar: avatar/email → dropdown → "Profile", "Logout"

### ProfilePage sections

**Section 1 — Identity**
- Display name (editable inline)
- Email (read only)
- Role badge (USER / ADMIN)
- "Change password" link (future)

**Section 2 — My Teams**
Table with columns: Team name | Domain | Developers | Sprints completed | Last sprint
Each row links to `/teams/:teamId/plan`
Empty state: "You haven't created any teams yet. Create your first team →"

**Section 3 — Stats**
Four stat cards in a row:
- Sprints planned
- Sprints completed
- Avg velocity accuracy (e.g. "74%")
- Goal hit rate (e.g. "60%")

**Section 4 — Memory Viewer**
This is the most important and unique section.
For each team the user owns, show a collapsible panel:

```
▼ Fintech Transfer Squad
  [Team Preferences]          [Sprint History]
  ┌─────────────────────┐    ┌─────────────────────┐
  │ ## Estimation        │    │ ## Sprint 4          │
  │ - Payment tasks:     │    │ Velocity: 38/30      │
  │   always raise to 5+ │    │ ...                  │
  └─────────────────────┘    └─────────────────────┘
  Last updated: 2 days ago    Last updated: 5 days ago
  [Clear preferences]         (read only)
```

Rules:
- Memory is displayed as **rendered markdown** — use a markdown renderer (e.g. `react-markdown`)
- `team_profile.md` has a "Clear preferences" button → `DELETE /api/teams/:teamId/memory/profile`
- `sprint_history.md` is read-only — never allow deletion
- If memory files don't exist yet → show "No preferences learned yet. Complete a sprint review to start."
- ADMIN can view memory for ALL teams, not just their own

### New frontend files
```
features/profile/
  ProfilePage.tsx
  TeamStatsCard.tsx
  MemoryViewer.tsx         — renders team_profile.md + sprint_history.md side by side
  useProfile.ts            — useQuery for /api/profile and /api/profile/stats
  useTeamMemory.ts         — useQuery for /api/teams/:teamId/memory per team

api/
  profileApi.ts            — getProfile(), getProfileStats(), getTeamMemory(teamId),
                             updateProfile(name), clearTeamProfile(teamId)
```

Install:
```bash
npm install react-markdown
```

Build in this exact order. One class per prompt.

```
infrastructure/auth/
  JwtConfig.java              — @ConfigurationProperties(prefix = "jwt")
  JwtService.java             — generateToken(), validateToken(), extractUserId()
  JwtAuthFilter.java          — OncePerRequestFilter, sets SecurityContext
  SecurityConfig.java         — @Configuration, @EnableWebSecurity, bean wiring

domain/model/
  AppUser.java                — @Entity: id UUID, email, passwordHash, createdAt

infrastructure/persistence/
  JpaUserRepository.java      — findByEmail(String email): Optional<AppUser>

web/auth/
  AuthController.java         — POST /api/auth/register, POST /api/auth/login
  AuthRequest.java            — record: email, name, password (name only required on register)
  AuthResponse.java           — record: token, userId, email, name, role
```

### JwtService contract
```java
public interface JwtService {
    String generateToken(UUID userId);
    boolean validateToken(String token);
    UUID extractUserId(String token);
}
```

### SecurityConfig rules
- `sessionManagement` → `STATELESS`
- Permit: `POST /api/auth/**`
- Authenticate: everything else
- Add `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`
- Disable CSRF (stateless JWT API)
- Password encoding: `BCryptPasswordEncoder`

### AuthController rules
- Registration: check email not already taken → hash password → save → return token
- Login: load user by email → verify BCrypt hash → return token
- Both return `AuthResponse` — never return the password hash

---

## 6. Auth — Frontend Classes to Build (Step 03–05)

```
store/
  authStore.ts               — Zustand: token, userId, email, login(), logout()

api/
  authApi.ts                 — login(email, password), register(email, password)

features/auth/
  LoginPage.tsx
  SignupPage.tsx

components/shared/
  ProtectedRoute.tsx         — redirects to /login if no token in authStore
```

### authStore contract
```typescript
interface AuthStore {
  token: string | null;
  userId: string | null;
  email: string | null;
  name: string | null;
  role: 'ADMIN' | 'USER' | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (token: string, userId: string, email: string, name: string, role: string) => void;
  logout: () => void;
}
```
Store persists to `localStorage` via Zustand persist middleware.
On logout: clear localStorage token AND call `queryClient.clear()` to wipe cached data.

### Router changes
Wrap all existing routes in `<ProtectedRoute>`.
Add public routes:
```
/login   → LoginPage
/signup  → SignupPage
```

### Axios interceptor — activate it now
The interceptor already exists as a placeholder in `api/axios.ts`.
Update it to read from `authStore` instead of raw `localStorage`:
```typescript
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Add 401 interceptor — auto logout on expired token
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

---

## 7. Sprint Close — Backend (Step 06)

`SprintReviewService` must do the following when a review is submitted:

```
1. Load sprint by sprintId
2. Mark each task as DONE or NOT_DONE based on taskCompletions map
3. Calculate per-developer: planned_pts, completed_pts
4. Save SprintReview entity
5. Save DevCompletion entity per developer
6. Update sprint status → COMPLETED
7. Trigger ReferenceBuilder.buildAndSave(sprintId)  ← Step 07
```

### Endpoint
```
POST /api/reviews
Body: SprintReviewRequest {
  sprintId: UUID
  goalResult: YES | PARTIALLY | NO
  blockerNote: String (nullable)
  taskCompletions: Map<UUID, Boolean>  // taskId -> completed
}
Response: 200 OK (no body needed)
```

### SprintReviewService contract
```java
@Service
public class SprintReviewService {
    public void submitReview(SprintReviewRequest request);
}
```

Constructor injection only. Dependencies:
- `SprintRepository`
- `SprintReviewRepository` (new — add to infrastructure/persistence)
- `ReferenceBuilder` (call after saving review)

---

## 8. Memory System — Backend (Step 07–08)

Two options:
Option A — Store in DB as text columns
Add a team_memory table:
sqlCREATE TABLE team_memory (
team_id UUID PRIMARY KEY REFERENCES team(id),
team_profile TEXT,
sprint_history TEXT,
team_profile_updated_at TIMESTAMP,
sprint_history_updated_at TIMESTAMP
);
Simple. One row per team. Memory is just a text column. MarkdownFileMemoryAdapter gets replaced by JpaMemoryAdapter that reads/writes this table instead of disk.
Changes needed:

New migration V2__add_team_memory.sql (shift existing V2/V3/V4/V5 to V3/V4/V5/V6)
New TeamMemory entity in domain/model
New JpaTeamMemoryRepository in infrastructure/persistence
Replace MarkdownFileMemoryAdapter with JpaMemoryAdapter implementing the same MemoryReader + MemoryWriter ports


### ReferenceBuilder (Step 07)
Triggered automatically after `SprintReviewService.submitReview()`.

```
Input:  sprintId
Logic:
  1. Load sprint + review + devCompletions from DB
  2. Load existing sprint_history.md for the team (via MemoryReader)
  3. Append new sprint block at the top
  4. Keep only the last 3 sprint blocks — trim older ones
  5. Write updated file (via MemoryWriter)
Output: updated sprint_history.md on disk
```

### sprint_history.md format to generate
```markdown
## Sprint {number} (completed)
**Goal:** {goal} | **Result:** {goalResult}
**Velocity:** {plannedPts} planned / {completedPts} actual

### Estimation accuracy
(skip on first sprint — no baseline yet)

### Developer actuals
| Developer | Planned | Completed | Completion Rate |
|-----------|---------|-----------|-----------------|
| {name}    | {pts}   | {pts}     | {pct}%          |

### Blockers
{blockerNote or "None reported"}
```

### PreferenceService (Step 08)
Triggered after the user approves the plan on the Review Screen.

```
Input:  sprintId
Logic:
  1. Load all EditLog entries for this sprintId from DB
  2. Group edits by field and task_type
  3. Detect patterns (e.g. "estimation raised 3+ times for backend tasks")
  4. Generate team_profile.md as structured markdown
  5. Write via MemoryWriter
Output: updated team_profile.md on disk
```

### team_profile.md format to generate
```markdown
# Team Preferences — {teamName}
Last updated: {date}

## Estimation
- {pattern detected from edit logs}

## Assignment
- {pattern detected from edit logs}

## Task Breakdown
- {pattern detected from edit logs}
```

Pattern detection rules:
- If same field edited in same direction 2+ times → write a preference
- If always reassigned from dev A to dev B for roleTag X → write assignment preference
- If story points always raised for a task type → write estimation preference

---

## 9. Continuous Mode Detection (Step 09)

`PlanningOrchestrator` currently always runs Flow A (fresh start).
Add detection logic:

```java
// In PlanningOrchestrator.planSprint():
boolean hasPriorHistory = sprintRepository
    .countCompletedByTeamId(request.teamId()) > 0;

String teamProfileMemory = hasPriorHistory
    ? memoryReader.readTeamProfile(request.teamId())
    : "";

String sprintHistoryMemory = hasPriorHistory
    ? memoryReader.readSprintHistory(request.teamId())
    : "";
```

Add to `SprintRepository`:
```java
long countCompletedByTeamId(UUID teamId);
```

No other changes needed — memory strings are already passed into all agent prompts.
Empty string = fresh start. Non-empty = continuous mode. Agents handle both.

---

## 10. Epic Input Formats (Step 10)

Currently: plain text only (one epic per line, optional `|` separator for description).

Add two more input modes on `PlanningPage`:

### Mode 1 — CSV upload
```
title,description
Add IBAN validation,Validate format and checksum on submit
Reconciliation report,Daily EOD report for settlements
```
Parse client-side with `papaparse`. Map rows to `Epic[]`. No backend change needed.

### Mode 2 — Excel upload (.xlsx)
Parse client-side with `SheetJS (xlsx)`.
Expected sheet format: Column A = title, Column B = description (optional).
Map rows to `Epic[]`. No backend change needed.

### UI change
Add a toggle above the epic textarea: `Text | CSV | Excel`
- Text mode: existing textarea (keep as default)
- CSV/Excel mode: file upload input, parse on change, populate the same `epics` state

### Frontend additions
```
features/planning/
  EpicFileUpload.tsx    — file input, parse CSV or Excel, call onEpicsLoaded(epics)
```

Install:
```bash
npm install papaparse xlsx
npm install --save-dev @types/papaparse
```

---

## 11. Trello Integration (Step 11–12)

### Decision: Trello per-team, not per-user
Each `Team` has an optional `trello_board_id`. The team creator links their Trello board once
during team setup. A single Trello API key + token is configured server-side in
`application.properties`. This avoids per-user OAuth complexity for now.

### application.properties additions
```properties
trello.api-key=${TRELLO_API_KEY}
trello.token=${TRELLO_TOKEN}
trello.base-url=https://api.trello.com/1
```

### TrelloApiClient contract
```java
@Component
public class TrelloApiClient {
    // Create a card on a list
    String createCard(String listId, String name, String description);

    // Get lists on a board
    List<TrelloList> getBoardLists(String boardId);

    // Find or create list by name on a board
    String findOrCreateList(String boardId, String listName);
}
```
Uses `WebClient` from Spring Web. No extra library needed.

### TrelloPlanPublisher flow
```
For each UserStory:
  - Find or create a Trello list named after the epic title on the board
  For each Task in the story:
    - Create a Trello card: title = task title, description = task description + acceptance criterion
    - Store returned card ID → task.trelloCardId
    - Save task back to DB
```

### Frontend: Trello push button
Already exists in ReviewPage as "Approve & Push to Trello".
After `approveMutation` succeeds → call `pushTrelloMutation`.
Show success state: "Plan pushed to Trello ✓"

---

## 16. New DB Migrations Needed

### V2__add_users.sql
```sql
CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### V3__add_team_ownership.sql
```sql
ALTER TABLE team ADD COLUMN owner_id UUID REFERENCES app_user(id);
```

### V4__add_sprint_review.sql
```sql
CREATE TABLE sprint_review (
    id UUID PRIMARY KEY,
    sprint_id UUID NOT NULL REFERENCES sprint(id),
    goal_result VARCHAR(20) NOT NULL,
    blocker_note TEXT,
    total_planned_pts INT NOT NULL DEFAULT 0,
    total_completed_pts INT NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE dev_completion (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES sprint_review(id),
    developer_id UUID NOT NULL REFERENCES developer(id),
    planned_pts INT NOT NULL DEFAULT 0,
    completed_pts INT NOT NULL DEFAULT 0
);
```

### V5__add_trello_member_id.sql
```sql
ALTER TABLE developer ADD COLUMN trello_member_id VARCHAR(100);
```

---

## 17. New API Endpoints Summary

| Method | Path | Controller | Step |
|---|---|---|---|
| POST | `/api/auth/register` | AuthController | 01 |
| POST | `/api/auth/login` | AuthController | 01 |
| GET | `/api/profile` | ProfileController | 08 |
| GET | `/api/profile/stats` | ProfileController | 08 |
| PUT | `/api/profile` | ProfileController | 08 |
| GET | `/api/teams/:teamId/memory` | TeamController | 09 |
| DELETE | `/api/teams/:teamId/memory/profile` | TeamController | 09 |
| POST | `/api/reviews` | ReviewController | 11 |
| GET | `/api/sprints/:sprintId/status` | SprintPlannerController | 11 |

---

## 18. Files to NOT Rebuild

These are done and working. Do not touch unless a step explicitly says to modify them.

- All domain entities (except add `ownerId` to Team and `trelloMemberId` to Developer)
- All port interfaces
- CapacityCalculator
- GeminiBreakdownAdapter, GeminiEstimationAdapter, GeminiDistributionAdapter
- GeminiClientProvider
- PlanningOrchestrator (only add continuous mode detection in Step 14)
- All JPA repository adapters
- MarkdownFileMemoryAdapter
- SprintPlannerController
- TeamController (only add ownership filter and memory endpoints in Steps 03 and 09)
- All frontend features: TeamSetupPage, PlanningPage, ReviewPage, SprintClosePage

---

## 19. Key Decisions (Rationale)

| Decision | Reason |
|---|---|
| First registered user is ADMIN | No seed script needed — developer registers first and gets full access |
| ADMIN bypasses all ownership checks | Developer can test all teams without switching accounts |
| Memory viewer is read-only for sprint_history | Outcomes are facts — only preferences (team_profile.md) should be editable |
| Clear preferences button exists | System may learn wrong patterns — PM must be able to reset |
| Profile stats computed server-side | Avoids heavy client-side aggregation across multiple API calls |
| name field added to AppUser | Profile page needs a display name — email alone is not user-friendly |
| JWT before Trello OAuth | Self-contained, no external dependency, can be tested immediately |
| Trello per-team not per-user | Avoids OAuth flow per user — one board linked at team setup |
| CSV/Excel parsed client-side | No file upload endpoint needed — keeps backend clean |
| ReferenceBuilder triggered server-side after review | Ensures memory is always updated even if user closes browser |
| PreferenceService triggered after plan approval | Edit log is complete only after approval — earlier trigger misses late edits |
| Continuous mode via DB count | Simple, reliable — no new flag needed on Team entity |

---

*Phase 1 completed: core AI pipeline working end-to-end*
*Phase 2 start: auth → roles → ownership → profile → memory visibility → sprint close → memory system → continuous mode → epic formats → Trello*
