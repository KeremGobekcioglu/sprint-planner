import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { LoadingSpinner } from '../../components/shared/LoadingSpinner';
import { PageLayout } from '../../components/shared/PageLayout';
import type { GoalResult } from '../../types/sprint';
import { TaskCompletionToggle } from './TaskCompletionToggle';
import { useSprintClose } from './useSprintClose';

interface CloseFormValues {
  goalResult: GoalResult;
  blockerNote: string;
  taskCompletions: Record<string, boolean>;
}

export function SprintClosePage() {
  const { sprintId } = useParams();
  const navigate = useNavigate();
  const { planQuery, reviewQuery, submitMutation } = useSprintClose(sprintId);
  const existingReview = reviewQuery.data ?? null;
  const effectiveSprintId = planQuery.data?.sprintId ?? sprintId;
  const [submitted, setSubmitted] = useState(false);

  const form = useForm<CloseFormValues>({
    defaultValues: {
      goalResult: 'PARTIALLY',
      blockerNote: '',
      taskCompletions: {},
    },
  });

  const tasks = useMemo(
    () => (planQuery.data?.userStories.flatMap((story) => story.tasks) ?? []).filter((t) => !t.deferred),
    [planQuery.data?.userStories],
  );
  const taskCompletions = form.watch('taskCompletions');

  const completedPoints = tasks.reduce((sum, task) => {
    if (!taskCompletions[task.id]) {
      return sum;
    }
    return sum + task.storyPoints;
  }, 0);

  const plannedPoints = tasks.reduce((sum, task) => sum + task.storyPoints, 0);

  const devCompletions = useMemo(() => {
    const byDeveloper = new Map<string, { plannedPts: number; completedPts: number }>();

    for (const task of tasks) {
      const developerId = task.assignedDevId;
      if (!developerId) {
        continue;
      }

      const current = byDeveloper.get(developerId) ?? { plannedPts: 0, completedPts: 0 };
      current.plannedPts += task.storyPoints;
      if (taskCompletions[task.id]) {
        current.completedPts += task.storyPoints;
      }
      byDeveloper.set(developerId, current);
    }

    return Array.from(byDeveloper.entries()).map(([developerId, points]) => ({
      developerId,
      plannedPts: points.plannedPts,
      completedPts: points.completedPts,
    }));
  }, [tasks, taskCompletions]);

  return (
    <PageLayout title="Sprint Close" subtitle="Capture completion and goal outcome to close the sprint.">
      <div className="mb-4">
        <button
          type="button"
          onClick={() => {
            if (planQuery.data?.sprintStatus === 'COMPLETED') {
              navigate('/dashboard', { replace: true });
            } else {
              navigate(`/sprints/${sprintId}/review`);
            }
          }}
          className="rounded-lg border border-slate-500/40 px-3 py-1.5 text-sm font-medium text-slate-300 hover:bg-slate-800/40"
        >
          {planQuery.data?.sprintStatus === 'COMPLETED' ? '← Back to Dashboard' : '← Back to Review'}
        </button>
      </div>
      {submitted && (
        <section className="space-y-3 rounded-3xl border border-emerald-500/20 bg-emerald-950/20 p-5 shadow-card">
          <div className="flex items-center gap-2">
            <span className="text-emerald-400 text-lg">✓</span>
            <p className="font-semibold text-emerald-200">Sprint review submitted successfully.</p>
          </div>
          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              onClick={() => navigate(`/sprints/${effectiveSprintId}/retrospective`)}
              className="rounded-lg bg-blue-500/85 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-600/85"
            >
              View Retrospective →
            </button>
            <button
              type="button"
              onClick={() => navigate('/dashboard', { replace: true })}
              className="rounded-lg border border-slate-500/40 px-4 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800/40"
            >
              Back to Dashboard
            </button>
          </div>
        </section>
      )}

      {existingReview && (
        <section className="space-y-3 rounded-3xl border border-emerald-500/20 bg-emerald-950/20 p-5 shadow-card">
          <div className="flex items-center gap-2">
            <span className="text-emerald-400 text-lg">✓</span>
            <p className="font-semibold text-emerald-200">This sprint has already been reviewed.</p>
          </div>
          <div className="grid gap-2 text-sm text-slate-300">
            <p><span className="text-slate-400">Goal result:</span> {existingReview.goalResult}</p>
            <p><span className="text-slate-400">Planned:</span> {existingReview.totalPlannedPts} pts</p>
            <p><span className="text-slate-400">Completed:</span> {existingReview.totalCompletedPts} pts</p>
            {existingReview.blockerNote && (
              <p><span className="text-slate-400">Blockers:</span> {existingReview.blockerNote}</p>
            )}
          </div>
          <button
            type="button"
            onClick={() => navigate(`/sprints/${effectiveSprintId}/retrospective`)}
            className="rounded-lg bg-blue-500/85 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-600/85"
          >
            View Retrospective →
          </button>
        </section>
      )}

      <section className={`space-y-4 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card${existingReview ? ' opacity-50 pointer-events-none' : ''}`}>
        {planQuery.isLoading ? <LoadingSpinner label="Loading sprint tasks..." /> : null}
        {planQuery.isError ? <ErrorBanner message="Could not load sprint tasks for closeout." /> : null}
        {submitMutation.isError ? (
          <ErrorBanner message="Failed to submit the sprint review." error={submitMutation.error} />
        ) : null}

        <form
          className="space-y-4"
          onSubmit={form.handleSubmit((values) => {
            if (!effectiveSprintId) {
              return;
            }

            if (!planQuery.data?.teamId) {
              return;
            }

            submitMutation.mutate(
              {
                teamId: planQuery.data.teamId,
                sprintId: effectiveSprintId,
                goalResult: values.goalResult,
                blockerNote: values.blockerNote || undefined,
                taskCompletions: values.taskCompletions,
                totalPlannedPts: plannedPoints,
                totalCompletedPts: completedPoints,
                devCompletions,
              },
              {
                onSuccess: () => {
                  setSubmitted(true);
                },
              },
            );
          })}
        >
          {!planQuery.data?.teamId ? (
            <ErrorBanner message="Sprint data is not available. Generate a plan again before submitting review." />
          ) : null}

          <div>
            <p className="mb-2 text-sm font-semibold text-slate-100">Task completion</p>
            <div className="space-y-2">
              {tasks.map((task) => (
                <TaskCompletionToggle
                  key={task.id}
                  taskId={task.id}
                  title={task.title}
                  checked={Boolean(taskCompletions[task.id])}
                  onChange={(taskId, checked) => {
                    const current = form.getValues('taskCompletions');
                    form.setValue(
                      'taskCompletions',
                      {
                        ...current,
                        [taskId]: checked,
                      },
                      { shouldDirty: true },
                    );
                  }}
                />
              ))}
            </div>
          </div>

          <div>
            <p className="mb-2 text-sm font-semibold text-slate-100">Sprint goal result</p>
            <select className="w-full rounded-lg border border-ink/20 px-3 py-2" {...form.register('goalResult')}>
              <option value="YES">Yes</option>
              <option value="PARTIALLY">Partially</option>
              <option value="NO">No</option>
            </select>
          </div>

          <div>
            <p className="mb-2 text-sm font-semibold text-slate-100">Blocker note (optional)</p>
            <textarea
              className="h-24 w-full rounded-lg border border-ink/20 px-3 py-2"
              {...form.register('blockerNote')}
            />
          </div>

          <div className="rounded-lg border border-pine/25 bg-emerald-50 p-3 text-sm font-medium text-pine">
            Completed points: {completedPoints}
          </div>

          <button
            className="w-full rounded-lg bg-ink px-4 py-2 font-semibold text-white sm:w-auto"
            disabled={submitMutation.isPending || !planQuery.data?.teamId || Boolean(existingReview)}
          >
            {submitMutation.isPending ? 'Submitting...' : 'Submit Review'}
          </button>
        </form>
      </section>
    </PageLayout>
  );
}
