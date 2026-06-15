import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getProfile, getProfileStats, updateProfile } from '../api/profileApi';

export const useProfile = () => {
  const queryClient = useQueryClient();

  const profileQuery = useQuery({
    queryKey: ['profile'],
    queryFn: getProfile,
    staleTime: 1000 * 60 * 5,
  });

  const statsQuery = useQuery({
    queryKey: ['profile-stats'],
    queryFn: getProfileStats,
    staleTime: 1000 * 60 * 5,
  });

  const updateNameMutation = useMutation({
    mutationFn: (name: string) => updateProfile(name),
    onSuccess: (updated) => {
      queryClient.setQueryData(['profile'], updated);
    },
    onError: (error) => {
      console.error('Profile update failed', error);
    },
  });

  return { profileQuery, statsQuery, updateNameMutation };
};
