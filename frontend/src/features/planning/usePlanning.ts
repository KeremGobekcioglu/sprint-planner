import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createSprintPlan } from '../../api/sprintApi';
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
    onSuccess: (data) => {
      queryClient.setQueryData(['sprint', data.sprintId], data);
    },
    onError: (error) => {
      console.error('Planning failed', error);
    },
  });

  return {
    developersQuery,
    planMutation,
  };
}
