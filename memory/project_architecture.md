---
name: project-architecture
description: Sprint Planner app — hexagonal Java/Spring backend + React frontend. Key architectural facts for future sessions.
metadata:
  type: project
---

Sprint Planner is a Java 21 / Spring Boot 3.5 + React (Vite/TS) monolith.

**Why:** AI sprint planning assistant — teams paste epics, AI pipeline breaks them down, estimates, assigns.

**Backend packages:** domain → application → infrastructure → web. Domain is pure Java (no Spring).

**Frontend:** `VITE_USE_REAL_API=true` env var switches between mock mode (localStorage) and real API. Default is mock mode.

**AI pipeline:** GeminiBreakdownAdapter → GeminiEstimationAdapter → CapacityCalculator (pure) → GeminiDistributionAdapter. All wired through port interfaces.

**Memory system:** `team_profile.md` (preferences, written after plan approval) and `sprint_history.md` (outcomes, written after sprint review) stored on disk via `MarkdownFileMemoryAdapter`. Read conditionally — only when team has ≥1 completed sprint.

**Auth:** JWT via jjwt. First registered user gets ADMIN role. All others get USER. Token stores userId + role in claims.

**DB:** PostgreSQL in production. Flyway migrations in `src/main/resources/db/migration/`. Entities are JPA-annotated domain models (not separate entity classes).

**How to apply:** When adding new features, always check which layer they belong to. Domain ports must stay clean (no Spring). Application layer only depends on domain ports. Web layer only calls application layer.
