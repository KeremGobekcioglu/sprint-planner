# Sprint Planner — Frontend Agent Context File

> Read this file at the start of every frontend session before generating any code.
> Also read AGENT_CONTEXT.md (backend) when you need to understand the API contract or data models.
> This is the single source of truth for frontend architecture, conventions, and build order.

---

## 1. Tech Stack

| Tool | Version | Purpose |
|---|---|---|
| Vite | latest | Build tool and dev server |
| React | 18 | UI framework |
| TypeScript | 5+ | Type safety — all files are `.tsx` or `.ts`, never `.js` |
| Tailwind CSS | 3 | Utility-first styling — no separate CSS files |
| shadcn/ui | latest | Component library — copy-paste, fully customizable |
| React Query (TanStack Query) | v5 | Server state, loading/error states, caching |
| Axios | latest | HTTP client — wrapped in a custom instance |
| React Router | v6 | Client-side routing |
| Zustand | latest | Client-only UI state (not server data) |
| React Hook Form | latest | Form state and validation |

### Why this stack for AI-driven development
- shadcn/ui components are copy-pasted into your repo — the agent can read and modify them directly
- Tailwind keeps styles in the same file as the component — agent never has to jump files
- React Query eliminates manual loading/error state boilerplate — agent generates less code
- Zustand is simpler than Redux — agent makes fewer mistakes with it
- TypeScript catches agent mistakes at compile time before you review

---

## 2. Project Structure

```
src/
├── api/                    # Axios instance + all API call functions
│   ├── axios.ts            # Base Axios instance with baseURL and interceptors
│   ├── sprintApi.ts        # Sprint planning API calls
│   ├── teamApi.ts          # Team and developer API calls
│   └── reviewApi.ts        # Sprint review API calls
│
├── components/             # Reusable UI components
│   ├── ui/                 # shadcn/ui generated components — do not hand-edit
│   └── shared/             # Your custom shared components
│       ├── PageLayout.tsx
│       ├── LoadingSpinner.tsx
│       ├── ErrorBanner.tsx
│       └── ConfirmDialog.tsx
│
├── features/               # Feature folders — one per screen/domain
│   ├── team-setup/
│   │   ├── TeamSetupPage.tsx
│   │   ├── DeveloperForm.tsx
│   │   └── useTeamSetup.ts
│   ├── planning/
│   │   ├── PlanningPage.tsx
│   │   ├── EpicInput.tsx
│   │   ├── DomainSelector.tsx
│   │   └── usePlanning.ts
│   ├── review/
│   │   ├── ReviewPage.tsx
│   │   ├── TaskCard.tsx
│   │   ├── MismatchWarning.tsx
│   │   ├── DeveloperLane.tsx
│   │   └── useReview.ts
│   └── sprint-close/
│       ├── SprintClosePage.tsx
│       ├── TaskCompletionToggle.tsx
│       └── useSprintClose.ts
│
├── hooks/                  # Generic reusable hooks
│   └── useDebounce.ts
│
├── store/                  # Zustand stores — client UI state only
│   ├── planningStore.ts    # Current planning session state
│   └── reviewStore.ts      # Review screen edits buffer
│
├── types/                  # TypeScript interfaces mirroring backend models
│   ├── team.ts
│   ├── sprint.ts
│   └── planning.ts
│
├── lib/                    # Utility functions
│   └── utils.ts            # shadcn/ui utility (cn function) + helpers
│
├── router.tsx              # React Router route definitions
└── main.tsx                # App entry point, QueryClientProvider, RouterProvider
```

---

## 3. TypeScript Types (Mirror Backend Models)

These must stay in sync with the backend data models in `AGENT_CONTEXT.md` Section 8.

```typescript
// types/team.ts
export interface Team {
  id: string;
  name: string;
  domain: 'E_COMMERCE' | 'FINTECH' | 'GENERIC';
  sprintLengthDays: number;
  trelloBoardId?: string;
  createdAt: string;
}

export interface Developer {
  id: string;
  teamId: string;
  name: string;
  role: 'backend' | 'frontend' | 'qa' | 'devops';
  baseVelocity: number;
}

// types/sprint.ts
export type SprintStatus = 'PLANNING' | 'IN_REVIEW' | 'ACTIVE' | 'COMPLETED';
export type GoalResult = 'YES' | 'PARTIALLY' | 'NO';

export interface Sprint {
  id: string;
  teamId: string;
  sprintNumber: number;
  goal: string;
  domainOverride?: string;
  status: SprintStatus;
  plannedAt: string;
  reviewedAt?: string;
}

export interface UserStory {
  id: string;
  sprintId: string;
  epicTitle: string;
  title: string;
  storyPoints: number;
  status: string;
  tasks: Task[];
}

export interface Task {
  id: string;
  storyId: string;
  assignedDevId?: string;
  title: string;
  description: string;
  acceptanceCriterion: string;
  roleTag: 'backend' | 'frontend' | 'qa' | 'devops';
  storyPoints: number;
  status: string;
  trelloCardId?: string;
}

// types/planning.ts
export interface Epic {
  title: string;
  description: string;
}

export interface PlanningRequest {
  teamId: string;
  epics: Epic[];
  domain: 'E_COMMERCE' | 'FINTECH' | 'GENERIC';
  sprintGoal: string;
  daysOff: Record<string, number>; // developerId -> days off
}

export interface SprintPlan {
  sprintId: string;
  stories: UserStory[];
  warnings: MismatchWarning[];
  totalPoints: number;
}

export interface MismatchWarning {
  type: 'VELOCITY' | 'ESTIMATION' | 'ASSIGNMENT' | 'GOAL';
  message: string;
  developerId?: string;
}

export interface SprintReviewRequest {
  sprintId: string;
  goalResult: GoalResult;
  blockerNote?: string;
  taskCompletions: Record<string, boolean>; // taskId -> completed
}
```

---

## 4. Axios Setup

```typescript
// api/axios.ts
import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
  timeout: 60000, // 60s — Gemini calls can take 10-30s
});

// Add auth token when Spring Security is enabled (Step 17)
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export default api;
```

---

## 5. API Functions

One file per backend controller. Functions are thin wrappers — no logic here.

```typescript
// api/sprintApi.ts
import api from './axios';
import { PlanningRequest, SprintPlan } from '../types/planning';

export const createSprintPlan = (request: PlanningRequest) =>
  api.post<SprintPlan>('/api/sprints/plan', request).then(r => r.data);

export const getSprintPlan = (sprintId: string) =>
  api.get<SprintPlan>(`/api/sprints/${sprintId}/plan`).then(r => r.data);

export const approvePlan = (sprintId: string) =>
  api.post(`/api/sprints/${sprintId}/approve`).then(r => r.data);

// api/teamApi.ts
import api from './axios';
import { Team, Developer } from '../types/team';

export const getTeams = () =>
  api.get<Team[]>('/api/teams').then(r => r.data);

export const createTeam = (team: Omit<Team, 'id' | 'createdAt'>) =>
  api.post<Team>('/api/teams', team).then(r => r.data);

export const getDevelopers = (teamId: string) =>
  api.get<Developer[]>(`/api/teams/${teamId}/developers`).then(r => r.data);

// api/reviewApi.ts
import api from './axios';
import { SprintReviewRequest } from '../types/planning';

export const submitReview = (request: SprintReviewRequest) =>
  api.post('/api/reviews', request).then(r => r.data);

export const logEdit = (sprintId: string, edit: object) =>
  api.post(`/api/sprints/${sprintId}/edits`, edit).then(r => r.data);
```

---

## 6. React Query Usage Rules

- **Server state** (data from the backend) → always React Query. Never put it in Zustand.
- **Client UI state** (form drafts, modal open/close, review edits buffer) → Zustand.
- Use `useMutation` for all POST/PUT/DELETE calls — never call Axios directly in a component.
- Always provide `onError` in mutations so errors surface to the user.
- Set `staleTime: 1000 * 60 * 5` (5 min) on team/developer data — it rarely changes.
- Planning results (`SprintPlan`) — `staleTime: 0`, always fresh.

```typescript
// Example pattern in a feature hook
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createSprintPlan } from '../../api/sprintApi';

export const usePlanning = () => {
  const queryClient = useQueryClient();

  const planMutation = useMutation({
    mutationFn: createSprintPlan,
    onSuccess: (data) => {
      queryClient.setQueryData(['sprint', data.sprintId], data);
    },
    onError: (error) => {
      console.error('Planning failed:', error);
    },
  });

  return { planMutation };
};
```

---

## 7. Screens and Routes

| Route | Component | Description |
|---|---|---|
| `/` | redirect to `/teams` | |
| `/teams` | `TeamSetupPage` | List teams, create new team, add developers |
| `/teams/:teamId/plan` | `PlanningPage` | Paste epics, select domain, trigger planning |
| `/sprints/:sprintId/review` | `ReviewPage` | Review and edit the generated plan |
| `/sprints/:sprintId/close` | `SprintClosePage` | Mark tasks done, submit sprint review |

---

## 8. Screen Descriptions

### TeamSetupPage (`/teams`)
- List existing teams with their domain and developer count
- Button to create new team (inline form or modal)
- On team select → navigate to planning page
- Developer management: add/remove developers, set role and base velocity

### PlanningPage (`/teams/:teamId/plan`)
- Textarea for epics (one per line or structured)
- Domain selector (E_COMMERCE / FINTECH / GENERIC) — pre-filled from team default
- Sprint goal input
- Days off per developer (simple number input per dev)
- "Generate Plan" button → `POST /api/sprints/plan`
- **Loading state is critical here** — Gemini takes 10–30s. Show a step-by-step progress indicator: "Breaking down epics… Estimating points… Calculating capacity… Assigning tasks…"
- On success → navigate to `/sprints/:sprintId/review`

### ReviewPage (`/sprints/:sprintId/review`)
- Most important screen. Nothing goes to Trello until the user approves here.
- Show `MismatchWarning` banners at the top if any warnings exist
- Tasks grouped by developer in swim lanes (`DeveloperLane`)
- Each `TaskCard` shows: title, story points, role tag, acceptance criterion
- Inline edit on every field (click to edit)
- Drag task between developer lanes to reassign
- "Defer to next sprint" action per task
- Total points per developer with capacity indicator (green/amber/red)
- "Approve & Push to Trello" button — disabled until user has seen all warnings
- Every edit is logged via `POST /api/sprints/:sprintId/edits`

### SprintClosePage (`/sprints/:sprintId/close`)
- Toggle per task: Done / Not Done
- Sprint goal result selector: Yes / Partially / No
- Optional blocker note textarea
- Auto-calculates actual points per developer from toggles
- "Submit Review" → `POST /api/reviews`
- On success → show summary and link back to team page

---

## 9. State Management Rules

### Zustand stores

```typescript
// store/reviewStore.ts — tracks edits made on the Review Screen
interface ReviewStore {
  edits: EditDiff[];           // accumulated edits before approval
  deferredTaskIds: string[];   // tasks deferred to next sprint
  dismissedWarnings: string[]; // warning types the PM dismissed
  addEdit: (edit: EditDiff) => void;
  deferTask: (taskId: string) => void;
  dismissWarning: (type: string) => void;
  reset: () => void;           // call after approval
}

// store/planningStore.ts — current session
interface PlanningStore {
  selectedTeamId: string | null;
  currentSprintId: string | null;
  setTeam: (teamId: string) => void;
  setSprint: (sprintId: string) => void;
}
```

### Rules
- Never put API response data into Zustand — that's React Query's job
- Reset `reviewStore` after the user approves a plan
- `planningStore` persists the current session — survives page refresh via `localStorage`

---

## 10. Component Rules (for the Agent)

1. **Every component is a function component** — no class components.
2. **Props are always typed** with a TypeScript interface defined above the component.
3. **No inline styles** — Tailwind classes only.
4. **shadcn/ui components** go in `src/components/ui/` and are never hand-edited.
5. **Feature components** go in their feature folder — not in `src/components/shared/`.
6. **Shared components** (used in 2+ features) go in `src/components/shared/`.
7. **Loading and error states are mandatory** in every component that fetches data.
8. **No `any` type** — if the type is unknown, use `unknown` and narrow it.
9. **Forms use React Hook Form** — never manage form state with `useState`.
10. **All API calls go through the `api/` layer** — never call Axios directly in a component.

---

## 11. Environment Variables

```bash
# .env.local (never commit this file)
VITE_API_BASE_URL=http://localhost:8080

# .env.production
VITE_API_BASE_URL=https://your-production-api.com
```

---

## 12. Backend API Contract (CORS)

Add this to your Spring Boot `application.properties` to allow the React dev server:

```properties
spring.web.cors.allowed-origins=http://localhost:5173
spring.web.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
spring.web.cors.allowed-headers=*
```

Or add a `@CrossOrigin` annotation on your controllers during development.

The React dev server runs on `http://localhost:5173` (Vite default).
The Spring Boot backend runs on `http://localhost:8080`.

---

## 13. Build Order — Follow This Exactly

Do not skip steps. One step = one agent prompt = one review.

```
STEP 01 — Project scaffold
          npm create vite@latest sprint-planner-ui -- --template react-ts
          Install: tailwindcss, @tanstack/react-query, axios, react-router-dom,
                   zustand, react-hook-form, @hookform/resolvers, zod

STEP 02 — shadcn/ui setup
          npx shadcn@latest init
          Add components as needed per step: Button, Input, Textarea,
          Select, Card, Badge, Dialog, Tooltip, Separator

STEP 03 — TypeScript types (src/types/)
          Copy types exactly from Section 3 above
          These are the contract between frontend and backend — do not invent new shapes

STEP 04 — Axios instance (src/api/axios.ts)
          Base URL from env var, 60s timeout, auth interceptor placeholder

STEP 05 — API functions (src/api/)
          sprintApi.ts, teamApi.ts, reviewApi.ts
          Thin wrappers only — no logic

STEP 06 — Router setup (src/router.tsx + src/main.tsx)
          All routes from Section 7
          Wrap app in QueryClientProvider and RouterProvider

STEP 07 — Zustand stores (src/store/)
          planningStore.ts and reviewStore.ts from Section 9

STEP 08 — Shared components
          PageLayout, LoadingSpinner, ErrorBanner, ConfirmDialog

STEP 09 — TeamSetupPage
          List teams, create team form, developer management
          Uses: useQuery(getTeams), useMutation(createTeam)

STEP 10 — PlanningPage
          Epic textarea, domain selector, days off inputs, Generate button
          Uses: useMutation(createSprintPlan)
          Critical: multi-step loading indicator during Gemini call

STEP 11 — ReviewPage skeleton
          Developer swim lanes, task cards (read-only first)
          Uses: useQuery(getSprintPlan)
          MismatchWarning banners

STEP 12 — ReviewPage interactions
          Inline edit per task field
          Drag-and-drop between developer lanes
          Defer to next sprint action
          Edit logging on every change

STEP 13 — ReviewPage approval
          Approve & Push to Trello button
          Disable until warnings are dismissed
          useMutation(approvePlan)

STEP 14 — SprintClosePage
          Task completion toggles
          Goal result selector
          useMutation(submitReview)

STEP 15 — Connect to real backend
          Verify each API call against running Spring Boot instance
          Fix any field name mismatches (camelCase frontend vs backend)
          Test full flow: team setup → planning → review → close

STEP 16 — Error handling pass
          Every useMutation gets onError with user-visible message
          Network timeout handling (60s Gemini calls)
          Empty states on all list views

STEP 17 — Polish
          Responsive layout (tablet + desktop — mobile is not a priority)
          Loading skeletons on data-heavy screens
          Keyboard navigation on ReviewPage
```

---

## 14. Agent Prompt Rules (for the Developer)

1. **Always paste the relevant TypeScript type** before asking for a component that uses it.
2. **Always paste the API function** before asking for a hook that calls it.
3. **One component or one hook per prompt** — never ask for a full page in one shot.
4. **After every generated file: check TypeScript errors first, review logic second.**
5. **If the agent uses `any` type — reject and re-prompt.**
6. **If the agent puts API calls directly in a component — reject and re-prompt.** It goes in the `api/` layer and called via React Query.
7. **If the agent uses `useState` for server data — reject and re-prompt.** That's React Query's job.
8. **Loading and error states are not optional** — if the agent omits them, reject and re-prompt.
9. **Build ReviewPage in two steps** — skeleton first (Step 11), interactions second (Step 12). It is the most complex screen.

---

## 15. Key Design Decisions (Rationale)

| Decision | Reason |
|---|---|
| Vite over CRA | CRA is unmaintained. Vite is faster and the current standard. |
| shadcn/ui over MUI | Components live in your repo — agent can read and modify them. MUI is a black box. |
| React Query for server state | Handles caching, loading, error, retry automatically. Less code for the agent to generate. |
| Zustand for client state | Simpler than Redux. Agent makes fewer mistakes. |
| 5 minutes Axios timeout | Gemini pipeline can take 10–30s. Default 5s would time out on every real call. |
| TypeScript strict mode | Agent mistakes caught at compile time before you review. |
| Feature folders | Each screen is self-contained. Agent works in one folder per session. |
| React Hook Form | Performant, less re-renders, integrates cleanly with Zod validation. |
| Properties not YAML | Consistency with backend config style. |

---

*Last updated: frontend setup phase — pre-implementation*
*Backend context: see AGENT_CONTEXT.md*
