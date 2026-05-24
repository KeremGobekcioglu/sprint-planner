import api from './axios';
import { getDevelopers, getTeams } from './teamApi';
import { getSampleEpicsForTeam, getSampleSprintGoal, SAMPLE_EPICS_PAYFLOW, SAMPLE_EPICS_SHOPHUB } from './sampleData';
import type { PlanningRequest, SprintPlan, SprintPlanWithId } from '../types/planning';

const USE_REAL_API = import.meta.env.VITE_USE_REAL_API === 'true';
const UUID_PATTERN = /[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i;

function getSprintPlanStorageKey(sprintId: string) {
  return `sprint-plan:${sprintId}`;
}

export function normalizeSprintId(rawSprintId: string): string {
  const trimmed = rawSprintId.trim();
  const exactUuid = trimmed.match(new RegExp(`^${UUID_PATTERN.source}$`, 'i'));
  if (exactUuid) {
    return trimmed;
  }

  const embeddedUuid = trimmed.match(UUID_PATTERN);
  if (embeddedUuid) {
    return embeddedUuid[0];
  }

  return trimmed;
}

function persistSprintPlan(plan: SprintPlan, sprintId: string) {
  if (typeof window === 'undefined') {
    return;
  }

  window.localStorage.setItem(getSprintPlanStorageKey(sprintId), JSON.stringify(plan));
}

function resolveSprintIdFromPlan(plan: SprintPlan): string {
  const sprintIds = Array.from(
    new Set(
      plan.userStories
        .map((story) => story.sprintId)
        .filter((value): value is string => Boolean(value && value.trim().length > 0)),
    ),
  );

  if (sprintIds.length === 1) {
    const [onlySprintId] = sprintIds;
    if (onlySprintId) {
      return onlySprintId;
    }
  }

  if (sprintIds.length > 1) {
    throw new Error('Planning response contains multiple sprint IDs.');
  }

  throw new Error('Planning response did not include sprintId on user stories.');
}

async function buildMockSprintPlan(request: PlanningRequest, sprintId: string): Promise<SprintPlan> {
  const developers = await getDevelopers(request.teamId);
  const developerCount = developers.length;

  const userStories: SprintPlan['userStories'] = request.epics.map((epic, epicIndex) => {
    const taskCount = Math.max(2, Math.min(4, developerCount > 0 ? developerCount : 2));

    const tasks = Array.from({ length: taskCount }, (_, taskIndex) => {
      const developer = developerCount > 0 ? developers[(epicIndex + taskIndex) % developerCount] : undefined;
      const storyPoints = [1, 2, 3, 5][(epicIndex + taskIndex) % 4] ?? 1;

      return {
        id: `${sprintId}-task-${epicIndex + 1}-${taskIndex + 1}`,
        storyId: `${sprintId}-story-${epicIndex + 1}`,
        assignedDevId: developer?.id,
        title: `${epic.title} - ${taskIndex === 0 ? 'Implementation' : taskIndex === 1 ? 'Validation' : `Follow-up ${taskIndex}`}`,
        description: epic.description,
        acceptanceCriterion: `Complete ${epic.title.toLowerCase()} work for ${request.sprintGoal}`,
        roleTag: developer?.capabilities?.[0] ?? 'backend',
        storyPoints,
        status: taskIndex === 0 ? 'TODO' : 'READY',
        trelloCardId: `${request.teamId}-${epicIndex + 1}-${taskIndex + 1}`,
      };
    });

    return {
      id: `${sprintId}-story-${epicIndex + 1}`,
      sprintId,
      epicTitle: epic.title,
      title: epic.description || epic.title,
      storyPoints: tasks.reduce((sum, task) => sum + task.storyPoints, 0),
      status: 'READY',
      tasks,
    };
  });

  const plan: SprintPlan = {
    teamId: request.teamId,
    sprintGoal: request.sprintGoal,
    userStories,
    unassignedTasks: [],
  };

  persistSprintPlan(plan, sprintId);
  return plan;
}

export const createSprintPlan = (request: PlanningRequest): Promise<SprintPlanWithId> => {
  if (!USE_REAL_API) {
    const sprintId = `mock-sprint-${request.teamId}`;
    return buildMockSprintPlan(request, sprintId).then((plan) => ({
      ...plan,
      sprintId,
    }));
  }

  return api.post<SprintPlan>('/api/sprints/plan', request).then((response) => {
    const sprintId = resolveSprintIdFromPlan(response.data);
    persistSprintPlan(response.data, sprintId);
    return {
      ...response.data,
      sprintId,
    };
  });
};

function readStoredSprintPlan(sprintId: string): SprintPlan | null {
  if (typeof window === 'undefined') {
    return null;
  }

  const raw = window.localStorage.getItem(getSprintPlanStorageKey(sprintId));
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as SprintPlan;
  } catch {
    return null;
  }
}

export const getSprintPlan = (sprintId: string): Promise<SprintPlanWithId> => {
  const canonicalSprintId = normalizeSprintId(sprintId);

  if (USE_REAL_API) {
    return api
      .get<SprintPlan>(`/api/sprints/${canonicalSprintId}/plan`)
      .then((response) => {
        persistSprintPlan(response.data, canonicalSprintId);
        return {
          ...response.data,
          sprintId: canonicalSprintId,
        };
      })
      .catch((error) => {
        const status = error?.response?.status as number | undefined;
        if (status === 401 || status === 403) {
          const storedPlan = readStoredSprintPlan(canonicalSprintId);
          if (storedPlan) {
            return {
              ...storedPlan,
              sprintId: canonicalSprintId,
            };
          }
        }
        return Promise.reject(error);
      });
  }

  if (typeof window === 'undefined') {
    return Promise.reject(new Error('Sprint plan storage is unavailable in this environment.'));
  }

  const raw = window.localStorage.getItem(getSprintPlanStorageKey(canonicalSprintId));
  if (!raw) {
    return Promise.reject(new Error(`Sprint plan not found for sprintId=${canonicalSprintId}`));
  }

  try {
    const plan = JSON.parse(raw) as SprintPlan;
    return Promise.resolve({
      ...plan,
      sprintId: canonicalSprintId,
    });
  } catch {
    return Promise.reject(new Error(`Stored sprint plan is invalid JSON for sprintId=${canonicalSprintId}`));
  }
};

export const approvePlan = (sprintId: string) => {
  const canonicalSprintId = normalizeSprintId(sprintId);

  if (!USE_REAL_API) {
    return Promise.resolve({ status: 'SKIPPED_BACKEND_STEP_12', sprintId: canonicalSprintId });
  }

  return api.post(`/api/sprints/${canonicalSprintId}/approve`).then((response) => response.data);
};

export const pushPlanToTrello = async (sprintId: string): Promise<void> => {
  await api.post(`/api/sprints/${normalizeSprintId(sprintId)}/trello-push`);
};

export interface MockPlanningDefaults {
  epicsText: string;
  sprintGoal: string;
  daysOff: Record<string, number>;
}

export function getMockPlanningDefaults(teamId: string): Promise<MockPlanningDefaults> {
  if (!USE_REAL_API) {
    return getDevelopers(teamId).then((developers) => {
      const epicsText = [
        'User Authentication | Implement login with OAuth2 and JWT tokens',
        'Payment Processing | Add Stripe integration for credit cards and wallets',
        'Inventory Management | Real-time stock updates and low-stock alerts',
        'Analytics Dashboard | Create performance metrics and reporting views',
      ].join('\n');

      const daysOff: Record<string, number> = {};
      developers.forEach((dev) => {
        daysOff[dev.id] = Math.random() > 0.7 ? 2 : 0;
      });

      return {
        epicsText,
        sprintGoal: 'Ship core features for Q2 launch',
        daysOff,
      };
    });
  }

  return Promise.reject(new Error('Mock defaults not available in real API mode'));
}

/**
 * Load sample epics for a team for rapid testing.
 * Returns pre-configured epics matching the team's domain (Fintech or E-Commerce).
 */
export interface ActiveSprintResponse {
  sprintId: string;
  status: string;
}

export const getActiveSprint = (): Promise<ActiveSprintResponse | null> => {
  if (!USE_REAL_API) {
    return Promise.resolve(null);
  }
  return api
    .get<ActiveSprintResponse>('/api/sprints/active')
    .then((r) => (r.status === 204 || !r.data ? null : r.data))
    .catch(() => null);
};

export const getPlanningMode = async (teamId: string) => {
  if (!USE_REAL_API) {
    // Mock: return FRESH if no completed sprints in mock data, else CONTINUOUS
    return { mode: 'FRESH', completedSprints: 0, hasTeamProfile: false, hasSprintHistory: false } as const;
  }
  const response = await api.get(`/api/sprints/teams/${teamId}/planning-mode`);
  return response.data;
};

export interface PlanningStep {
  phase: string;
  message: string;
  at: string;
  done: boolean;
  error: boolean;
}

export const getPlanningStatus = (): Promise<PlanningStep[]> => {
  if (!USE_REAL_API) return Promise.resolve([]);
  return api.get<PlanningStep[]>('/api/sprints/plan/status').then((r) => r.data ?? []);
};

export async function getSampleEpicsForPlanning(
  teamId: string,
): Promise<{
  epicsText: string;
  sprintGoal: string;
  daysOff: Record<string, number>;
}> {
  // Try to detect the team's domain and return domain-appropriate sample epics.
  // If the team is one of the explicit sample teams we still return their dedicated epics.
  const teams = await getTeams();
  const team = teams.find((t) => t.id === teamId);

  let epics = getSampleEpicsForTeam(teamId);
  if ((!epics || epics.length === 0) && team) {
    if (team.domain === 'FINTECH') {
      epics = SAMPLE_EPICS_PAYFLOW;
    } else if (team.domain === 'E_COMMERCE') {
      epics = SAMPLE_EPICS_SHOPHUB;
    }
  }

  const sprintGoal = getSampleSprintGoal(teamId) || (team?.domain === 'FINTECH'
    ? 'Deliver core fintech capabilities this sprint'
    : team?.domain === 'E_COMMERCE'
    ? 'Ship e-commerce improvements and reduce friction'
    : 'Complete planned features on schedule');

  const developers = await getDevelopers(teamId);

  const epicsText = epics.map((epic) => `${epic.title} | ${epic.description}`).join('\n');

  const daysOff: Record<string, number> = {};
  developers.forEach((dev) => {
    daysOff[dev.id] = 0; // No days off for sample data
  });

  return {
    epicsText,
    sprintGoal,
    daysOff,
  };
}

