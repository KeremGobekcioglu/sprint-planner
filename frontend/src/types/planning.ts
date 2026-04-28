import type { GoalResult, UserStory } from './sprint';
import type { TeamDomain } from './team';

export interface Epic {
  title: string;
  description: string;
}

export interface PlanningRequest {
  teamId: string;
  epics: Epic[];
  domain: TeamDomain;
  sprintGoal: string;
  daysOff: Record<string, number>;
}

export interface MismatchWarning {
  type: 'VELOCITY' | 'ESTIMATION' | 'ASSIGNMENT' | 'GOAL';
  message: string;
  developerId?: string;
}

export interface SprintPlan {
  teamId: string;
  sprintGoal: string;
  userStories: UserStory[];
}

/**
 * Frontend-only: SprintPlan with sprintId added for navigation and storage.
 * The backend doesn't return sprintId, so we add it on the frontend.
 */
export interface SprintPlanWithId extends SprintPlan {
  sprintId: string;
}

export interface SprintReviewRequest {
  teamId: string;
  sprintId: string;
  goalResult: GoalResult;
  blockerNote?: string;
  taskCompletions: Record<string, boolean>;
  totalPlannedPts?: number;
  totalCompletedPts?: number;
  devCompletions?: Array<{
    developerId: string;
    plannedPts: number;
    completedPts: number;
  }>;
}
