import api from './axios';
import type { PlanningRequest, SprintPlan } from '../types/planning';

interface BackendSprintPlan {
  teamId: string;
  sprintGoal: string;
  userStories: SprintPlan['stories'];
}

function getSprintPlanStorageKey(sprintId: string) {
  return `sprint-plan:${sprintId}`;
}

function persistSprintPlan(plan: SprintPlan) {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.setItem(getSprintPlanStorageKey(plan.sprintId), JSON.stringify(plan));
}

function mapBackendSprintPlan(plan: BackendSprintPlan): SprintPlan {
  const sprintId = plan.userStories[0]?.sprintId ?? crypto.randomUUID();
  const totalPoints = plan.userStories.reduce((sum, story) => sum + story.storyPoints, 0);

  return {
    teamId: plan.teamId,
    sprintGoal: plan.sprintGoal,
    sprintId,
    stories: plan.userStories,
    warnings: [],
    totalPoints,
  };
}

export const createSprintPlan = (request: PlanningRequest) =>
  api.post<BackendSprintPlan>('/api/planning/sprints/plan', request).then((response) => {
    const plan = mapBackendSprintPlan(response.data);
    persistSprintPlan(plan);
    return plan;
  });

export const getSprintPlan = (sprintId: string) => {
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('Sprint plan storage is unavailable in this environment.'));
  }

  const raw = window.localStorage.getItem(getSprintPlanStorageKey(sprintId));
  if (!raw) {
    return Promise.reject(new Error(`Sprint plan not found for sprintId=${sprintId}`));
  }

  try {
    return Promise.resolve(JSON.parse(raw) as SprintPlan);
  } catch (error) {
    return Promise.reject(new Error(`Stored sprint plan is invalid JSON for sprintId=${sprintId}`));
  }
};

export const approvePlan = async (_sprintId: string) => {
  return { status: 'SKIPPED_BACKEND_STEP_12' };
};
