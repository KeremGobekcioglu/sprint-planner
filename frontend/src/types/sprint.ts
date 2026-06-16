import type { CapabilityTag } from './team';

export type UUID = string;

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
  roleTag: CapabilityTag;
  storyPoints: number;
  status: string;
  trelloCardId?: string;
  assignmentReason?: string;
  deferred?: boolean;
}
