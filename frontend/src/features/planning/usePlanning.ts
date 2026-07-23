import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createSprintPlan, getPlanningStatus } from '../../api/sprintApi';
import { getDevelopers } from '../../api/teamApi';

export function usePlanning(teamId: string | undefined) {
  const queryClient = useQueryClient();

  const developersQuery = useQuery({
    queryKey: ['developers', teamId],
    queryFn: () => getDevelopers(teamId as string),
    enabled: Boolean(teamId),
    staleTime: 1000 * 60 * 5,
  });

  const planMutation = useMutation({
    mutationFn: createSprintPlan,
    onMutate: () => {
      queryClient.setQueryData(['planning-status'], []);
    },
    onSuccess: (data) => {
      queryClient.setQueryData(['sprint', data.sprintId], data);
    },
    onError: (error) => {
      console.error('Planning failed', error);
    },
  });

  const statusQuery = useQuery({
    queryKey: ['planning-status'],
    queryFn: getPlanningStatus,
    enabled: planMutation.isPending,
    refetchInterval: planMutation.isPending ? 2500 : false,
    staleTime: 0,
  });

  const rawSteps = statusQuery.data ?? [];
  // While the mutation is still pending, a completed "done" step means we're seeing stale
  // data from the previous run (the status poll raced the backend's start() call).
  // Return [] until the fresh run's own steps arrive.
  const planningSteps =
    planMutation.isIdle
      ? []
      : planMutation.isPending && rawSteps.some((s) => s.done)
        ? []
        : rawSteps;

  return {
    developersQuery,
    planMutation: {
      ...planMutation,
      submittedAt: planMutation.submittedAt ?? null,
    },
    planningSteps,
  };
}
