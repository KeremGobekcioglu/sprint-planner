import { create } from 'zustand';
import type { EditPayload } from '../api/reviewApi';

interface ReviewStore {
  edits: EditPayload[];
  deferredTaskIds: string[];
  dismissedWarnings: string[];
  addEdit: (edit: EditPayload) => void;
  deferTask: (taskId: string) => void;
  dismissWarning: (type: string) => void;
  reset: () => void;
}

export const useReviewStore = create<ReviewStore>((set) => ({
  edits: [],
  deferredTaskIds: [],
  dismissedWarnings: [],
  addEdit: (edit) => set((state) => ({ edits: [...state.edits, edit] })),
  deferTask: (taskId) =>
    set((state) => {
      if (state.deferredTaskIds.includes(taskId)) {
        return state;
      }
      return { deferredTaskIds: [...state.deferredTaskIds, taskId] };
    }),
  dismissWarning: (type) =>
    set((state) => {
      if (state.dismissedWarnings.includes(type)) {
        return state;
      }
      return { dismissedWarnings: [...state.dismissedWarnings, type] };
    }),
  reset: () =>
    set({
      edits: [],
      deferredTaskIds: [],
      dismissedWarnings: [],
    }),
}));
