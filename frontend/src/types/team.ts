export type TeamDomain = 'E_COMMERCE' | 'FINTECH' | 'GENERIC';
export type DeveloperRole = 'backend' | 'frontend' | 'qa' | 'devops';

export interface Team {
  id: string;
  name: string;
  domain: TeamDomain;
  sprintLengthDays: number;
  trelloBoardId?: string;
  createdAt: string;
}

export interface Developer {
  id: string;
  teamId: string;
  name: string;
  role: DeveloperRole;
  baseVelocity: number;
}
