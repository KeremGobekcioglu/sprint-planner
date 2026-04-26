import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { approvePlan } from '../../api/sprintApi';
import { logEdit, type EditPayload } from '../../api/reviewApi';
import type { SprintPlan } from '../../types/planning';

export function useReview(sprintId: string | undefined) {
  const queryClient = useQueryClient();

  const planQuery = useQuery<SprintPlan | undefined>({
    queryKey: ['sprint', sprintId],
    queryFn: async () => queryClient.getQueryData<SprintPlan>(['sprint', sprintId]),
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

  const logEditMutation = useMutation({
    mutationFn: ({ id, edit }: { id: string; edit: EditPayload }) => logEdit(id, edit),
    onError: (error) => {
      console.error('Edit logging failed', error);
    },
  });

  return {
    planQuery,
    approveMutation,
    logEditMutation,
  };
}
