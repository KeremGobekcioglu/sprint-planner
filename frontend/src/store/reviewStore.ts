import { create } from 'zustand';
import type { EditPayload } from '../api/reviewApi';

interface ReviewStore {
  edits: EditPayload[];
  deferredTaskIds: string[];
  dismissedWarnings: string[];
  addEdit: (edit: EditPayload) => void;
  deferTask: (taskId: string) => void;
  toggleDeferred: (taskId: string) => void;
  isDeferred: (taskId: string) => boolean;
  dismissWarning: (type: string) => void;
  reset: () => void;
}

export const useReviewStore = create<ReviewStore>((set, get) => ({
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
  toggleDeferred: (taskId) =>
    set((state) => {
      const isCurrentlyDeferred = state.deferredTaskIds.includes(taskId);
      if (isCurrentlyDeferred) {
        return {
          deferredTaskIds: state.deferredTaskIds.filter((id) => id !== taskId),
        };
      } else {
        return { deferredTaskIds: [...state.deferredTaskIds, taskId] };
      }
    }),
  isDeferred: (taskId) => get().deferredTaskIds.includes(taskId),
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
