import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useParams } from 'react-router-dom';
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
  const { planQuery, submitMutation } = useSprintClose(sprintId);
  const [submitted, setSubmitted] = useState(false);

  const form = useForm<CloseFormValues>({
    defaultValues: {
      goalResult: 'PARTIALLY',
      blockerNote: '',
      taskCompletions: {},
    },
  });

  const tasks = useMemo(() => planQuery.data?.stories.flatMap((story) => story.tasks) ?? [], [planQuery.data?.stories]);
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
      <section className="space-y-4 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card">
        {planQuery.isLoading ? <LoadingSpinner label="Loading sprint tasks..." /> : null}
        {planQuery.isError ? <ErrorBanner message="Could not load sprint tasks for closeout." /> : null}

        {!submitted ? (
          <form
            className="space-y-4"
            onSubmit={form.handleSubmit((values) => {
              if (!sprintId) {
                return;
              }

              if (!planQuery.data?.teamId) {
                return;
              }

              submitMutation.mutate(
                {
                  teamId: planQuery.data.teamId,
                  sprintId,
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
              className="rounded-lg bg-ink px-4 py-2 font-semibold text-white"
              disabled={submitMutation.isPending || !planQuery.data?.teamId}
            >
              {submitMutation.isPending ? 'Submitting...' : 'Submit Review'}
            </button>
          </form>
        ) : (
          <div className="space-y-3 rounded-2xl border border-pine/30 bg-emerald-50 p-4">
            <p className="text-lg font-bold text-pine">Sprint review submitted.</p>
            <p className="text-sm text-pine/80">Total completed points: {completedPoints}</p>
            <Link className="inline-block rounded-lg bg-pine px-4 py-2 text-sm font-semibold text-white" to="/teams">
              Back to Teams
            </Link>
          </div>
        )}
      </section>
    </PageLayout>
  );
}
