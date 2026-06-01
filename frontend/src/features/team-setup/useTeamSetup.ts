import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createDeveloper,
  createTeam,
  getDevelopers,
  getTeams,
  removeDeveloper,
  type DeveloperCreateRequest,
  type TeamCreateRequest,
} from '../../api/teamApi';
import type { Team } from '../../types/team';

const TEAM_QUERY_KEY = ['teams'];

export function useTeamSetup(selectedTeamId: string | null) {
  const queryClient = useQueryClient();

  const teamsQuery = useQuery({
    queryKey: TEAM_QUERY_KEY,
    queryFn: getTeams,
    staleTime: 1000 * 60 * 5,
  });

  const developersQuery = useQuery({
    queryKey: ['developers', selectedTeamId],
    queryFn: () => getDevelopers(selectedTeamId as string),
    enabled: Boolean(selectedTeamId),
    staleTime: 1000 * 60 * 5,
  });

  const createTeamMutation = useMutation({
    mutationFn: (payload: TeamCreateRequest) => createTeam(payload),
    onSuccess: (team) => {
      queryClient.setQueryData<Team[]>(TEAM_QUERY_KEY, (current) => {
        const existing = current ?? [];
        if (existing.some((item) => item.id === team.id)) {
          return existing;
        }
        return [...existing, team];
      });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: (error) => {
      console.error('Create team failed', error);
    },
  });

  const createDeveloperMutation = useMutation({
    mutationFn: ({ teamId, payload }: { teamId: string; payload: DeveloperCreateRequest }) =>
      createDeveloper(teamId, payload),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['developers', variables.teamId] });
    },
    onError: (error) => {
      console.error('Create developer failed', error);
    },
  });

  const removeDeveloperMutation = useMutation({
    mutationFn: ({ teamId, developerId }: { teamId: string; developerId: string }) =>
      removeDeveloper(teamId, developerId),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['developers', variables.teamId] });
    },
    onError: (error) => {
      console.error('Remove developer failed', error);
    },
  });

  return {
    teamsQuery,
    developersQuery,
    createTeamMutation,
    createDeveloperMutation,
    removeDeveloperMutation,
  };
}
