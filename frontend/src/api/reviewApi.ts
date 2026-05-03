import api from './axios';
import { normalizeSprintId } from './sprintApi';
import type { SprintReviewRequest } from '../types/planning';

export interface EditPayload {
  field: string;
  entityId: string;
  oldValue: string | number | boolean | null;
  newValue: string | number | boolean | null;
}

interface ReviewHttpDto {
  id: string;
  sprintId: string;
  goalResult: SprintReviewRequest['goalResult'];
  blockerNote: string;
  totalPlannedPts: number;
  totalCompletedPts: number;
  submittedAt: string;
  devCompletions: Array<{
    id: string;
    reviewId: string;
    developerId: string;
    plannedPts: number;
    completedPts: number;
  }>;
}

interface SubmitReviewHttpRequest {
  teamId: string;
  latestReview: ReviewHttpDto;
  rollingWindowReviews: ReviewHttpDto[];
  editLogs: Array<{
    id: string;
    sprintId: string;
    field: string;
    originalValue: string;
    newValue: string;
    taskType: string;
    loggedAt: string;
  }>;
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

  const reviewId = crypto.randomUUID();
  const latestReview: ReviewHttpDto = {
    id: reviewId,
    sprintId: request.sprintId,
    goalResult: request.goalResult,
    blockerNote: request.blockerNote ?? '',
    totalPlannedPts: request.totalPlannedPts ?? 0,
    totalCompletedPts: request.totalCompletedPts ?? 0,
    submittedAt: new Date().toISOString(),
    devCompletions: (request.devCompletions ?? []).map((completion) => ({
      id: crypto.randomUUID(),
      reviewId,
      developerId: completion.developerId,
      plannedPts: completion.plannedPts,
      completedPts: completion.completedPts,
    })),
  };

  const payload: SubmitReviewHttpRequest = {
    teamId: request.teamId,
    latestReview,
    rollingWindowReviews: [latestReview],
    editLogs: [],
  };

  return api.post('/api/reviews', payload).then((response) => response.data);
};

export const logEdit = (sprintId: string, edit: EditPayload) => {
  const canonicalSprintId = normalizeSprintId(sprintId);

  if (!USE_REAL_API) {
    return Promise.resolve({ status: 'SKIPPED_BACKEND_STEP_12', sprintId: canonicalSprintId, edit });
  }

  return api.post(`/api/sprints/${canonicalSprintId}/edits`, edit).then((response) => response.data);
};
