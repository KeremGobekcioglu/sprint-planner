import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getDevelopers } from '../../api/teamApi';
import { approvePlan, getSprintPlan } from '../../api/sprintApi';
import { logEdit, type EditPayload } from '../../api/reviewApi';
import type { SprintPlan } from '../../types/planning';

export function useReview(sprintId: string | undefined) {
  const queryClient = useQueryClient();

  const planQuery = useQuery<SprintPlan | undefined>({
    queryKey: ['sprint', sprintId],
    queryFn: async () => {
      const cachedPlan = queryClient.getQueryData<SprintPlan>(['sprint', sprintId]);
      if (cachedPlan) {
        return cachedPlan;
      }
      if (!sprintId) {
        return undefined;
      }
      return getSprintPlan(sprintId);
    },
    enabled: Boolean(sprintId),
    staleTime: Infinity,
    retry: false,
  });

  const approveMutation = useMutation({
    mutationFn: (id: string) => approvePlan(id),
    onError: (error) => {
      console.error('Approve failed', error);
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

  const logEditMutation = useMutation({
    mutationFn: ({ id, edit }: { id: string; edit: EditPayload }) => logEdit(id, edit),
    onError: (error) => {
      console.error('Edit logging failed', error);
    },
  });

  return {
    planQuery,
    developersQuery,
    approveMutation,
    logEditMutation,
  };
}
