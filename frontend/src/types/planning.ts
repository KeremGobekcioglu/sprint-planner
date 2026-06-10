import type { GoalResult, UserStory } from './sprint';
import type { TeamDomain } from './team';

export interface Epic {
  title: string;
  description: string;
}

export interface CarryoverTask {
  taskId: string;
  sprintId: string;
  sprintGoal: string;
  epicTitle: string;
  storyTitle: string;
  taskTitle: string;
  description: string;
  acceptanceCriterion: string;
  roleTag: string;
  storyPoints: number;
  previousAssignedDevId: string | null;
  previousAssignedDevName: string | null;
}

export interface PlanningRequest {
  teamId: string;
  epics: Epic[];
  domain: TeamDomain;
  sprintGoal: string;
  daysOff: Record<string, number>;
  previousSprintContext?: string;
  carryoverTasks?: CarryoverTask[];
}

export interface SprintConfidenceForecast {
  commitmentPoints: number;
  realisticCompletionPoints: number;
  recommendedDeferPoints: number;
  completionProbability: number;
  historicalCompletionRate: number;
  sprintsAnalyzed: number;
  forecastSummary: string;
  insights: string[];
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
  unassignedTasks: UserStory['tasks'];
  sprintStatus?: string;
  confidence?: SprintConfidenceForecast;
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
