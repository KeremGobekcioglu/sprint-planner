import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getDevelopers, getTeam } from '../../api/teamApi';
import { approvePlan, getSprintPlan, pushPlanToTrello } from '../../api/sprintApi';
import { logEdit, type EditPayload } from '../../api/reviewApi';
import type { SprintPlanWithId } from '../../types/planning';

export function useReview(sprintId: string | undefined) {
  const queryClient = useQueryClient();

  const planQuery = useQuery<SprintPlanWithId | undefined>({
    queryKey: ['sprint', sprintId],
    queryFn: async () => {
      if (!sprintId) {
        return undefined;
      }
      return getSprintPlan(sprintId);
    },
    enabled: Boolean(sprintId),
    staleTime: 0,
    refetchOnMount: 'always',
    retry: false,
  });

  const approveMutation = useMutation({
    mutationFn: (id: string) => approvePlan(id),
    onError: (error) => {
      console.error('Approve failed', error);
    },
  });

  const pushTrelloMutation = useMutation({
    mutationFn: (id: string) => pushPlanToTrello(id),
    onError: (error) => {
      console.error('Mock Trello push failed', error);
    },
  });

  const teamId = planQuery.data?.teamId;

  const developersQuery = useQuery({
    queryKey: ['developers', teamId],
    queryFn: () => getDevelopers(teamId as string),
    enabled: Boolean(teamId),
    staleTime: Infinity,
    retry: false,
  });

  const teamQuery = useQuery({
    queryKey: ['team', teamId],
    queryFn: () => getTeam(teamId as string),
    enabled: Boolean(teamId),
    staleTime: Infinity,
    retry: false,
  });

  const logEditMutation = useMutation({
    mutationFn: ({ id, edit }: { id: string; edit: EditPayload }) => logEdit(id, edit),
    onError: (error) => {
      console.error('Edit logging failed', error);
    },
  });

  return {
    planQuery,
    developersQuery,
    teamQuery,
    approveMutation,
    pushTrelloMutation,
    logEditMutation,
  };
}
