import api from './axios';
import type { PlanningRequest, SprintPlan } from '../types/planning';

interface BackendSprintPlan {
  teamId: string;
  sprintGoal: string;
  userStories: SprintPlan['stories'];
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
  api.post<BackendSprintPlan>('/api/planning/sprints/plan', request).then((response) => mapBackendSprintPlan(response.data));

export const getSprintPlan = (_sprintId: string) => {
  return Promise.reject(new Error('GET sprint plan endpoint is not available in backend Step 12.'));
};

export const approvePlan = async (_sprintId: string) => {
  return { status: 'SKIPPED_BACKEND_STEP_12' };
};
