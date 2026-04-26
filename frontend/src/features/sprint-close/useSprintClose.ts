import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getSprintPlan } from '../../api/sprintApi';
import { submitReview } from '../../api/reviewApi';
import type { SprintPlan } from '../../types/planning';

export function useSprintClose(sprintId: string | undefined) {
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

  const submitMutation = useMutation({
    mutationFn: submitReview,
    onError: (error) => {
      console.error('Submit review failed', error);
    },
  });

  return {
    planQuery,
    submitMutation,
  };
}
