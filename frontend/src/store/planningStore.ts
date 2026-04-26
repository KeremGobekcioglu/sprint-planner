import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface PlanningStore {
  selectedTeamId: string | null;
  currentSprintId: string | null;
  setTeam: (teamId: string) => void;
  setSprint: (sprintId: string) => void;
}

export const usePlanningStore = create<PlanningStore>()(
  persist(
    (set) => ({
      selectedTeamId: null,
      currentSprintId: null,
      setTeam: (teamId) => set({ selectedTeamId: teamId }),
      setSprint: (sprintId) => set({ currentSprintId: sprintId }),
    }),
    {
      name: 'planning-session',
    },
  ),
);
