import api from './axios';
import type { UUID } from '../types/sprint';

export interface TeamMemoryResponse {
  teamProfile: string | null;
  sprintHistory: string | null;
  teamProfileLastUpdated: string | null;
  sprintHistoryLastUpdated: string | null;
}

export interface TeamSummary {
  teamId: string;
  name: string;
  domain: string;
  developerCount: number;
  completedSprints: number;
  lastSprintAt: string | null;
}

export interface ProfileResponse {
  userId: string;
  email: string;
  name: string;
  role: 'ADMIN' | 'USER';
  createdAt: string;
  teams: TeamSummary[];
}

export interface ProfileStatsResponse {
  totalSprintsPlanned: number;
  totalSprintsCompleted: number;
  averageVelocityAccuracy: number;
  goalHitRate: number;
  mostActiveTeam: string | null;
}

export interface UpdateProfileRequest {
  name: string;
}

export const getProfile = (): Promise<ProfileResponse> =>
  api.get<ProfileResponse>('/api/profile').then((r) => r.data);

export const getProfileStats = (): Promise<ProfileStatsResponse> =>
  api.get<ProfileStatsResponse>('/api/profile/stats').then((r) => r.data);

export const updateProfile = (name: string): Promise<ProfileResponse> =>
  api.put<ProfileResponse>('/api/profile', { name }).then((r) => r.data);

export const getTeamMemory = async (teamId: UUID): Promise<TeamMemoryResponse> => {
  const response = await api.get<TeamMemoryResponse>(`/api/teams/${teamId}/memory`);
  return response.data;
};

export const clearTeamProfile = (teamId: UUID): Promise<void> =>
  api.delete(`/api/teams/${teamId}/memory/profile`).then(() => undefined);

export interface TrelloStatusResponse {
  connected: boolean;
  username: string | null;
}

export const getTrelloStatus = async (): Promise<TrelloStatusResponse> => {
  const response = await api.get<TrelloStatusResponse>('/api/auth/trello/status');
  return response.data;
};

export const saveTrelloToken = async (token: string): Promise<void> => {
  await api.post('/api/auth/trello/token', { token });
};

export const disconnectTrello = async (): Promise<void> => {
  await api.delete('/api/auth/trello/token');
};
