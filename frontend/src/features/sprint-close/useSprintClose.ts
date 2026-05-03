import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getSprintPlan } from '../../api/sprintApi';
import { submitReview } from '../../api/reviewApi';
import type { SprintPlanWithId } from '../../types/planning';

export function useSprintClose(sprintId: string | undefined) {
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
