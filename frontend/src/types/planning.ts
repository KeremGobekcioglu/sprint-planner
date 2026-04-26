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
  sprintId: string;
  stories: UserStory[];
  warnings: MismatchWarning[];
  totalPoints: number;
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
