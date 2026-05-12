import { useQuery } from '@tanstack/react-query';
import { UUID } from '../types/sprint';
import { getTeamMemory, TeamMemoryResponse } from '../api/profileApi';

export const useTeamMemory = (teamId: UUID | null) => {
  return useQuery<TeamMemoryResponse>({
    queryKey: ['team-memory', teamId],
    queryFn: () => getTeamMemory(teamId!),
    enabled: teamId != null,
    staleTime: 1000 * 60 * 5, // 5 minutes
  });
};

