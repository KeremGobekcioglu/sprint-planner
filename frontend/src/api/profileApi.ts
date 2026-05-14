import api from './axios';
import type { UUID } from '../types/sprint';

export interface TeamMemoryResponse {
  teamProfile: string | null;
  sprintHistory: string | null;
  teamProfileLastUpdated: string | null;
  sprintHistoryLastUpdated: string | null;
}

export const getTeamMemory = async (teamId: UUID): Promise<TeamMemoryResponse> => {
  const response = await api.get<TeamMemoryResponse>(`/api/teams/${teamId}/memory`);
  return response.data;
};
