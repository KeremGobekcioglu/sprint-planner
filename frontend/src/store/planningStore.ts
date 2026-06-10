import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { SprintConfidenceForecast } from '../types/planning';

interface PlanningStore {
  selectedTeamId: string | null;
  currentSprintId: string | null;
  confidence: SprintConfidenceForecast | null;
  setTeam: (teamId: string) => void;
  setSprint: (sprintId: string) => void;
  setConfidence: (forecast: SprintConfidenceForecast | null) => void;
}

export const usePlanningStore = create<PlanningStore>()(
  persist(
    (set) => ({
      selectedTeamId: null,
      currentSprintId: null,
      confidence: null,
      setTeam: (teamId) => set({ selectedTeamId: teamId }),
      setSprint: (sprintId) => set({ currentSprintId: sprintId }),
      setConfidence: (forecast) => set({ confidence: forecast }),
    }),
    {
      name: 'planning-session',
    },
  ),
);
