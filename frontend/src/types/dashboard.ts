import type { TeamSummary } from '../api/profileApi';

export type { TeamSummary };

export interface ActiveSprintSummary {
  sprintId: string;
  teamId: string;
  teamName: string;
  teamDomain: string;
  sprintGoal: string;
  status: 'PLANNING' | 'IN_REVIEW' | 'ACTIVE' | 'COMPLETED';
  plannedAt: string;
  storyCount: number;
  totalPoints: number;
}

export interface RecentSprintSummary {
  sprintId: string;
  teamId: string;
  teamName: string;
  sprintGoal: string;
  goalResult: 'YES' | 'PARTIALLY' | 'NO' | null;
  plannedPts: number;
  completedPts: number;
  submittedAt: string | null;
}

export interface DashboardResponse {
  activeSprints: ActiveSprintSummary[];
  teams: TeamSummary[];
  recentSprints: RecentSprintSummary[];
}
