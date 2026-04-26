import api from './axios';
import type { Developer, Team } from '../types/team';

export interface TeamCreateRequest {
  name: string;
  domain: Team['domain'];
  sprintLengthDays: number;
  trelloBoardId?: string;
}

export interface DeveloperCreateRequest {
  name: string;
  role: Developer['role'];
  baseVelocity: number;
}

export const getTeams = () =>
  api
    .get<Team[]>('/api/teams')
    .then((response) => response.data)
    .catch((error: unknown) => {
      console.warn('Team list endpoint is unavailable in backend Step 12.', error);
      return [];
    });

export const createTeam = (team: TeamCreateRequest) =>
  api.post<Team>('/api/teams', team).then((response) => response.data);

export const getDevelopers = (teamId: string) =>
  api.get<Developer[]>(`/api/teams/${teamId}/developers`).then((response) => response.data);

export const createDeveloper = (teamId: string, developer: DeveloperCreateRequest) =>
  api.post<Developer>(`/api/teams/${teamId}/developers`, developer).then((response) => response.data);

export const removeDeveloper = (teamId: string, developerId: string) =>
  api
    .delete(`/api/teams/${teamId}/developers/${developerId}`)
    .then((response) => response.data)
    .catch((error: unknown) => {
      console.warn('Remove developer endpoint is unavailable in backend Step 12.', error);
      return { status: 'SKIPPED_BACKEND_STEP_12', teamId, developerId };
    });
