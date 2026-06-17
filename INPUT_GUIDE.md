# INPUT_GUIDE.md

> How to enter data into Sprint Planner — team setup, epics, sprint goal, codebase context, and all planning inputs.
> Every detail here comes from reading the actual source code (`DeveloperForm.tsx`, `EpicInput.tsx`, `PlanningPage.tsx`).

---

## Step 1 — Team Setup

**Where:** `/teams` → select or create a team → Add Developer

Do this once per team, before planning. Each developer you add becomes an assignable slot in the AI pipeline.

### Developer fields

| Field | Required | What to enter |
|---|---|---|
| **Name** | Yes | Full name or display name. e.g. `Alice Chen` |
| **Capabilities** | Yes | Checkboxes (see table below) |
| **Job title** | Yes | Free text. e.g. `Senior Backend Engineer`. Display only. |
| **Sprint velocity** | Yes | Story points per sprint (number, min 1). Typical range: **3–13** |

### Capability checkboxes

| Tag | Tasks routed here |
|---|---|
| `backend` | API endpoints, database queries, server-side logic |
| `frontend` | UI components, forms, client-side interactions |
| `qa` | Test plans, automated tests, bug verification |
| `devops` | CI/CD, infrastructure, deployments, monitoring |
| `android` | Android-specific mobile tasks |
| `ios` | iOS-specific mobile tasks |
| `fullstack` | Tasks explicitly tagged fullstack |

**Rules:**
- The AI **only assigns a task to a developer whose capabilities include the task's role tag**. A `backend` task will never be assigned to a developer who only has `frontend` checked.
- Select **all** capabilities that apply. A fullstack developer who can do both backend and frontend should have both `backend` and `frontend` checked — not just `fullstack`.
- If no capabilities are set, the system infers them from the job title (e.g. a title containing "Backend" maps to `backend`). It is safer to set them explicitly.

### Sprint velocity

Story points this developer can realistically complete in a full sprint at 100% availability.

- This is the **ceiling** for how much work they are assigned.
- It is reduced automatically for days off: a developer with 5 days off in a 10-day sprint gets roughly half their velocity allocated.
- After completed sprints, the system also applies a **historical completion rate** (weighted average of past sprints) — so an over-allocated developer from previous sprints will get a slightly lower allocation next sprint automatically.
- Typical values: junior `3–5`, mid `5–8`, senior `8–13`.

---

## Step 2 — Sprint Planning

**Where:** `/teams/:teamId/plan`

### 2a. Carry-over Tasks *(shown automatically)*

If your previous sprint ended with `NOT_DONE` tasks, a panel appears at the very top before the form.

Each entry shows:
- Task title
- Role tag (e.g. `BACKEND`)
- Story points
- Who it was previously assigned to

**Check** the tasks you want to bring into this sprint. They are injected as a synthetic story (`Carry-over from previous sprint`) and go through the estimation and distribution phases like any other task. **Uncheck** tasks you want to defer permanently — they are simply ignored.

Use **Select all** / **Deselect all** for convenience.

---

### 2b. Import Previous Sprint History *(Fresh Start mode only)*

Only visible on the **first sprint** with a team (no completed sprint history yet).

Paste free text or upload a `.txt`/`.md` file describing your previous sprint. There is no required format — write naturally:

```
Sprint goal: Launch checkout flow
Velocity: 34/40 pts completed
Blockers: Payment integration took longer than estimated — Stripe webhook docs were outdated
Team notes: Alice was out 2 days, QA backlog was larger than expected
```

This is used as a one-time substitute for the sprint history memory that normally builds up after completed sprints. It is **not persisted** — it only influences this one planning run.

In **Continuous mode** (after your first completed sprint), this panel is hidden because the AI already has your history.

---

### 2c. Codebase Context *(optional)*

**Toggle:** click **📁 Add codebase context** to expand.

Free-text description of your tech stack and current codebase state. The AI uses this in two places:

1. **Breakdown phase** — avoids generating tasks for things already built (e.g. won't generate "set up auth" if you say auth is complete)
2. **Task guidance panel (💡)** — tailors implementation hints to your specific stack

The field is **pre-filled** from the most recent sprint's saved context, so you rarely have to type it from scratch. Edit or clear it as needed each sprint.

**Good example:**

```
Stack: React 18, Spring Boot 3, PostgreSQL 16
Auth: complete (JWT, BCrypt)
Team CRUD: complete
Sprint CRUD: complete
In progress: payment flow ~50% done — Stripe checkout session created, webhook not wired yet
Conventions: hexagonal architecture, constructor injection, Flyway migrations, REST APIs
Constraints: must support mobile browsers, no new npm dependencies without team review
```

**What makes a good codebase context:**
- List your actual stack (language, framework, DB)
- State what is **already done** so the AI doesn't regenerate it
- Note what is **in progress** and roughly how complete
- Mention any important conventions or constraints the AI should respect

**What to leave out:** team names, sprint goals, deadlines — those go in other fields.

---

### 2d. Epics

The core input. Two entry modes — switch with the **📋 Cards / 📄 Paste** toggle.

#### Cards mode

One card per epic. Add cards with **+ Add epic**, remove with **✕**.

| Sub-field | Required | What to enter |
|---|---|---|
| **Title** | Yes | Short name for the feature area. e.g. `Checkout Flow` |
| **Description** | No | What needs to be built. e.g. `Implement cart, order summary, and payment step with form validation` |

A descriptive epic produces better task breakdown than a title-only epic. One sentence is enough.

#### Paste mode

Bulk import — one epic per line. All of the following formats are accepted and produce identical results:

```
Checkout Flow | Implement cart and payment with validation
Payment Integration - Stripe webhook handling and refund flow
Search Autosuggest: Add autosuggest dropdown to the search bar
3. Inventory Sync
[PROJ-123] User Notifications
Dashboard Analytics
```

**How the parser works:**

| Input format | Title | Description |
|---|---|---|
| `Title \| Description` | `Title` | `Description` |
| `Title - Description` | `Title` | `Description` |
| `Title: Description` | `Title` | `Description` |
| `1. Title` | `Title` | *(empty)* |
| `[PROJ-123] Title` | `Title` | *(empty)* |
| `Title` | `Title` | *(empty)* |

Notes:
- The split happens on the **first** occurrence only — a description can contain `|`, `-`, or `:` freely.
- ` - ` requires spaces on both sides. `Title-Description` (no spaces) is treated as a title-only line.
- Leading numbered prefixes (`1.`, `2.`) and Jira-style ticket prefixes (`[PROJ-123]`) are stripped automatically.
- Empty lines are ignored.

Switch back to **Cards** mode after pasting to review and edit the parsed result before submitting.

**Minimum:** at least one epic with a non-empty title. The form will not submit without it.

---

### 2e. Domain

3-button toggle. Pick the closest match to your product:

| Option | Effect |
|---|---|
| `GENERIC` | Default. Balanced task generation across all role types. |
| `FINTECH` | AI adds compliance checks, audit logging, and security review tasks. |
| `E_COMMERCE` | AI adds analytics instrumentation, A/B test scaffolding, and conversion-related tasks. |

This affects what the AI generates during breakdown. You can override per-sprint even if your team has a default domain set.

---

### 2f. Sprint Goal

- **Required.**
- One sentence. Describes what success looks like for this sprint.
- The AI uses the sprint goal to focus task generation — a vague goal produces generic tasks.

| Quality | Example |
|---|---|
| Too vague | `Improve the app` |
| Better | `Launch the checkout flow` |
| Best | `Ship the end-to-end checkout flow so users can browse, add items to cart, and complete a purchase` |

---

### 2g. Days Off by Developer

Number input per developer, shown as a row for each team member.

- Enter how many **sprint days** each person is unavailable (PTO, public holidays, sick leave).
- Leave `0` for a fully available developer.
- Only values greater than `0` are sent to the backend — zeros are stripped.

**How it affects capacity:**

```
available_ratio = max(0.5, 1 - (total_days_off / (dev_count × sprint_length_days)))
```

A developer with 5 days off in a 10-day sprint has an availability ratio of ~0.5, which halves the points allocated to them.

---

## Quick Checklist Before Generating

- [ ] Each developer has the right **capabilities** checked
- [ ] Sprint **velocity** is realistic for each developer
- [ ] At least one **epic** with a non-empty title
- [ ] **Sprint goal** is specific and one sentence
- [ ] **Days off** entered for anyone who is out this sprint
- [ ] **Codebase context** describes what is already built (avoids redundant tasks)
- [ ] Carry-over tasks selected if you want unfinished work from last sprint included

---

## What Happens After You Click Generate Plan

1. The AI pipeline runs in 4 sequential phases — a live log appears on screen:
   - **Memory** — loads team preferences and sprint history
   - **AI Breakdown** — generates user stories and tasks from your epics
   - **AI Estimation** — assigns story points (`XS/S/M/L/XL` → Fibonacci)
   - **AI Assignment** — distributes tasks to developers by capability and capacity

2. Navigation is **blocked** while the pipeline runs to prevent accidental page exits. It auto-releases after 90 seconds or on error.

3. On success, a **result view** appears showing:
   - Total stories, tasks, and story points
   - Full pipeline log
   - Sprint confidence forecast (based on your team's historical completion rate)

4. Choose **Review Plan →** to go directly to the review screen, or **Go to Dashboard** to access the plan later.

---

## After Planning — Review Screen

On the review screen (`/sprints/:sprintId/review`) you can:

- **Drag tasks** between developer lanes to reassign them
- **Edit** task titles, acceptance criteria, and story points inline
- **Defer** tasks with the toggle (logged as an edit)
- **View AI reasoning** — each task shows a one-sentence assignment reason beneath its title
- **Open 💡 guidance** — click the lightbulb on any task to get implementation hints, a suggested approach, and risk flags from Gemini (loaded once per task, cached for the session)
- **Approve** the plan to move the sprint to ACTIVE (triggers team preference learning from your edits)
- **Push to Trello** — requires your Trello account connected in Profile
