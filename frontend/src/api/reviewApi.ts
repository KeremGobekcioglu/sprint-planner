import api from './axios';
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

export const submitReview = (request: SprintReviewRequest) => {
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

  return api.post('/api/reviews/process', payload).then((response) => response.data);
};

export const logEdit = async (_sprintId: string, _edit: EditPayload) => {
  return { status: 'SKIPPED_BACKEND_STEP_12' };
};
