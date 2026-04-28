import api from './axios';
import { getDevelopers } from './teamApi';
import type { PlanningRequest, SprintPlan } from '../types/planning';

const USE_REAL_API = import.meta.env.VITE_USE_REAL_API === 'true';

function getSprintPlanStorageKey(sprintId: string) {
  return `sprint-plan:${sprintId}`;
}

function persistSprintPlan(plan: SprintPlan) {
  if (typeof window === 'undefined') {
    return;
  }

  window.localStorage.setItem(getSprintPlanStorageKey(plan.sprintId), JSON.stringify(plan));
}

async function buildMockSprintPlan(request: PlanningRequest, sprintId: string): Promise<SprintPlan> {
  const developers = await getDevelopers(request.teamId);
  const developerCount = developers.length;

  const stories: SprintPlan['stories'] = request.epics.map((epic, epicIndex) => {
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
        roleTag: developer?.role ?? 'backend',
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

  const warnings =
    request.epics.length === 0
      ? [
          {
            type: 'GOAL' as const,
            message: 'No epics were supplied, so the sprint plan was created with a placeholder structure.',
          },
        ]
      : [];

  const plan: SprintPlan = {
    teamId: request.teamId,
    sprintGoal: request.sprintGoal,
    sprintId,
    stories,
    warnings,
    totalPoints: stories.reduce((sum, story) => sum + story.storyPoints, 0),
  };

  persistSprintPlan(plan);
  return plan;
}

export const createSprintPlan = (request: PlanningRequest) => {
  if (!USE_REAL_API) {
    return buildMockSprintPlan(request, `mock-sprint-${request.teamId}`);
  }

  return api.post<SprintPlan>('/api/sprints/plan', request).then((response) => {
    persistSprintPlan(response.data);
    return response.data;
  });
};

export const getSprintPlan = (sprintId: string) => {
  if (USE_REAL_API) {
    return api.get<SprintPlan>(`/api/sprints/${sprintId}/plan`).then((response) => {
      persistSprintPlan(response.data);
      return response.data;
    });
  }

  if (typeof window === 'undefined') {
    return Promise.reject(new Error('Sprint plan storage is unavailable in this environment.'));
  }

  const raw = window.localStorage.getItem(getSprintPlanStorageKey(sprintId));
  if (!raw) {
    return Promise.reject(new Error(`Sprint plan not found for sprintId=${sprintId}`));
  }

  try {
    return Promise.resolve(JSON.parse(raw) as SprintPlan);
  } catch {
    return Promise.reject(new Error(`Stored sprint plan is invalid JSON for sprintId=${sprintId}`));
  }
};

export const approvePlan = (sprintId: string) => {
  if (!USE_REAL_API) {
    return Promise.resolve({ status: 'SKIPPED_BACKEND_STEP_12', sprintId });
  }

  return api.post(`/api/sprints/${sprintId}/approve`).then((response) => response.data);
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
