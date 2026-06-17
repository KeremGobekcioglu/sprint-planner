import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getSprintPlan, getSprintReview } from '../../api/sprintApi';
import type { SprintReviewSummary } from '../../api/sprintApi';
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

  const reviewQuery = useQuery<SprintReviewSummary | null>({
    queryKey: ['sprint-review', sprintId],
    queryFn: () => (sprintId ? getSprintReview(sprintId) : Promise.resolve(null)),
    enabled: Boolean(sprintId),
    staleTime: Infinity,
    retry: false,
  });

  const submitMutation = useMutation({
    mutationFn: submitReview,
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['sprint', variables.sprintId] });
    },
    onError: (error) => {
      console.error('Submit review failed', error);
    },
  });

  return {
    planQuery,
    reviewQuery,
    submitMutation,
  };
}
