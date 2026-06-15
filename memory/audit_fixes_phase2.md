---
name: audit-fixes-phase2
description: Phase 2 audit findings and all fixes applied in a single session. Reference for what was broken and how it was resolved.
metadata:
  type: project
---

Phase 2 full audit + fix session completed.

**Critical fixes applied:**

1. **POST /api/reviews mismatch fixed** — frontend was sending `{ teamId, latestReview, rollingWindowReviews, editLogs }` (nested) but backend expects flat `{ teamId, sprintId, goalResult, blockerNote, taskCompletions, totalPlannedPts, totalCompletedPts }`. Fixed in `frontend/src/api/reviewApi.ts`.

2. **LogEdit type mismatch fixed** — frontend was sending boolean/number values for `oldValue`/`newValue` but backend `LogEditHttpRequest` expects `String`. Added `toStringValue()` converter in `reviewApi.ts`.

3. **ReferenceBuilder wired** — `SprintReviewService` now calls `referenceBuilder.updateSprintHistory(teamId, reviews)` after marking sprint COMPLETED. Constructor injection added.

4. **PreferenceService wired** — `SprintPlannerController.approveSprint()` now calls `preferenceService.updateTeamProfile(teamId, editLogs)` after approving. Constructor injection added.

5. **Continuous mode detection added** — `PlanningOrchestrator` now checks `sprintRepository.countCompletedByTeamId(teamId) > 0` before reading memory files. Added `SprintRepository` as constructor dependency.

6. **Back buttons added** — PlanningPage (→ /teams), ReviewPage (→ /teams/:teamId/plan), SprintClosePage (→ /sprints/:id/review, only during form).

7. **DevDataSeeder created** — `infrastructure/config/DevDataSeeder.java`, `@Profile("dev")`, seeds `dev@sprint.local` / `dev123` with Team Orbit (FINTECH), Team Atlas (E_COMMERCE), Team Pulse (GENERIC). `spring.profiles.active=dev` added to application.properties.

8. **ProfilePage created** — `features/profile/ProfilePage.tsx` with 4 sections (Identity, My Teams, Stats, Memory Viewer). Route `/profile` added to `router.tsx`. Profile link in `PageLayout` navbar (click email → /profile).

9. **profileApi.ts completed** — added `getProfile()`, `getProfileStats()`, `updateProfile()`, `clearTeamProfile()`.

10. **useProfile.ts implemented** — queries profile + stats, mutation for updateName.

11. **GlobalExceptionHandler.java added** — handles IllegalArgumentException→400, ResponseStatusException→pass-through, Exception→500. Response shape: `{ error, status }`.

12. **ErrorBanner updated** — accepts optional `error` prop, extracts `.response.data.error` from Axios errors to show backend message.

13. **Error banners added to UI** — ReviewPage shows errors for approveMutation and pushTrelloMutation. SprintClosePage shows errors for submitMutation.

14. **GET /api/sprints/active added** — finds active/planning/in-review sprints for current user's teams. Returns 204 if none found.

15. **findActiveForTeams added** to SprintRepository port + JpaSprintRepository + SprintJpaRepository.

16. **Active sprint detection on TeamSetupPage** — `useQuery(getActiveSprint)` on load, shows `NoticeBanner` with "Continue review →" link when active sprint exists.

17. **NoticeBanner updated** — now accepts optional `action: { label, onClick }` prop for a button.

**What's still missing (not fixed this session):**
- Sprint number hardcoded to 1 in SprintPlannerController (Bug 15)
- GET /api/sprints/:sprintId/status endpoint (Bug 21)
- Capacity display in ReviewPage uses raw baseVelocity (Bug 16)
- SAMPLE_DEVELOPERS/SAMPLE_TEAMS imports in TeamSetupPage from sampleData.ts — these are unused in the component body (dead imports)

**How to apply:** When working on sprint review, planning approval, or memory features — these are all now fully wired. If reviewing the codebase state post-Phase 2, these are all in place.
