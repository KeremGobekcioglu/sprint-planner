import api from './axios';
import { normalizeSprintId } from './sprintApi';
import type { SprintReviewRequest } from '../types/planning';

export interface EditPayload {
  field: string;
  entityId: string;
  oldValue: string | number | boolean | null;
  newValue: string | number | boolean | null;
  taskType?: string;
}

const USE_REAL_API = import.meta.env.VITE_USE_REAL_API === 'true';

interface MockReviewResult {
  success: true;
  reviewId: string;
  sprintId: string;
  teamId: string;
  summary: {
    goalResult: SprintReviewRequest['goalResult'];
    blockerNote: string;
    totalPlannedPts: number;
    totalCompletedPts: number;
  };
}

function buildMockReviewResult(request: SprintReviewRequest): MockReviewResult {
  return {
    success: true,
    reviewId: crypto.randomUUID(),
    sprintId: request.sprintId,
    teamId: request.teamId,
    summary: {
      goalResult: request.goalResult,
      blockerNote: request.blockerNote ?? '',
      totalPlannedPts: request.totalPlannedPts ?? 0,
      totalCompletedPts: request.totalCompletedPts ?? 0,
    },
  };
}

export const submitReview = (request: SprintReviewRequest) => {
  if (!USE_REAL_API) {
    return Promise.resolve(buildMockReviewResult(request));
  }

  return api
    .post('/api/reviews', {
      teamId: request.teamId,
      sprintId: request.sprintId,
      goalResult: request.goalResult,
      blockerNote: request.blockerNote ?? null,
      taskCompletions: request.taskCompletions,
      totalPlannedPts: request.totalPlannedPts ?? 0,
      totalCompletedPts: request.totalCompletedPts ?? 0,
    })
    .then((response) => response.data);
};

function toStringValue(value: string | number | boolean | null): string | null {
  if (value === null || value === undefined) return null;
  return String(value);
}

export const logEdit = (sprintId: string, edit: EditPayload) => {
  const canonicalSprintId = normalizeSprintId(sprintId);

  if (!USE_REAL_API) {
    return Promise.resolve({ status: 'SKIPPED_BACKEND_STEP_12', sprintId: canonicalSprintId, edit });
  }

  return api
    .post(`/api/sprints/${canonicalSprintId}/edits`, {
      field: edit.field,
      entityId: edit.entityId,
      oldValue: toStringValue(edit.oldValue),
      newValue: toStringValue(edit.newValue),
      taskType: edit.taskType ?? 'UNKNOWN',
    })
    .then((response) => response.data);
};
