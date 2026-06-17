export type TeamDomain = 'E_COMMERCE' | 'FINTECH' | 'GENERIC';
export type CapabilityTag = string;

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
  role: string;
  capabilities: CapabilityTag[];
  baseVelocity: number;
  trelloMemberId?: string;
  seniority?: string;
}
