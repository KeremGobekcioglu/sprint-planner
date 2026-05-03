import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { LoadingSpinner } from '../../components/shared/LoadingSpinner';
import { PageLayout } from '../../components/shared/PageLayout';
import { useReviewStore } from '../../store/reviewStore';
import type { MismatchWarning as MismatchWarningType } from '../../types/planning';
import type { Task } from '../../types/sprint';
import { DeveloperLane } from './DeveloperLane';
import { MismatchWarning } from './MismatchWarning';
import { useReview } from './useReview';

export function ReviewPage() {
  const { sprintId } = useParams();
  const navigate = useNavigate();
  const { dismissedWarnings, dismissWarning, deferTask, addEdit, reset } = useReviewStore();
  const { planQuery, developersQuery, approveMutation, pushTrelloMutation, logEditMutation } = useReview(sprintId);
  const [overrides, setOverrides] = useState<Record<string, string>>({});
  const effectiveSprintId = planQuery.data?.sprintId;

  const stories = planQuery.data?.userStories ?? [];
  const warnings: MismatchWarningType[] = [];
  const developerLabels = useMemo(() => {
    return new Map(
      (developersQuery.data ?? []).map((developer) => [developer.id, `${developer.name} • ${developer.role}`]),
    );
  }, [developersQuery.data]);

  const allTasks = useMemo(() => stories.flatMap((story) => story.tasks), [stories]);

  const lanes = useMemo(() => {
    const laneMap = new Map<string, Task[]>();
    for (const task of allTasks) {
      const effectiveOwner = overrides[task.id] ?? task.assignedDevId ?? 'unassigned';
      const laneTasks = laneMap.get(effectiveOwner) ?? [];
      laneTasks.push({ ...task, assignedDevId: effectiveOwner });
      laneMap.set(effectiveOwner, laneTasks);
    }
    return Array.from(laneMap.entries());
  }, [allTasks, overrides]);

  const hasReviewedAllWarnings = warnings.every((warning) => dismissedWarnings.includes(warning.type));

  return (
    <PageLayout
      title="Review"
      subtitle="Inspect, edit and rebalance the plan before pushing anything to Trello."
      actions={
        <button
          className="rounded-lg bg-ink px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
          disabled={
            !planQuery.data ||
            !hasReviewedAllWarnings ||
            approveMutation.isPending ||
            pushTrelloMutation.isPending ||
            !effectiveSprintId
          }
          onClick={() => {
            if (!effectiveSprintId) return;
            approveMutation.mutate(effectiveSprintId, {
              onSuccess: () => {
                pushTrelloMutation.mutate(effectiveSprintId, {
                  onSuccess: () => {
                    reset();
                    navigate(`/sprints/${effectiveSprintId}/close`);
                  },
                });
              },
            });
          }}
        >
          {approveMutation.isPending
            ? 'Approving...'
            : pushTrelloMutation.isPending
              ? 'Pushing to Trello...'
              : 'Approve & Push to Trello'}
        </button>
      }
    >
      <section className="space-y-5">
        {planQuery.isLoading ? <LoadingSpinner label="Loading sprint review..." /> : null}
        {planQuery.isError ? <ErrorBanner message="Could not load the sprint plan." /> : null}
        {!planQuery.isLoading && !planQuery.data ? (
          <ErrorBanner message="Sprint data is not available. Generate a plan again before opening review." />
        ) : null}

        <div className="grid gap-3">
          {warnings.map((warning) => (
            <MismatchWarning
              key={`${warning.type}-${warning.message}`}
              warning={warning}
              dismissed={dismissedWarnings.includes(warning.type)}
              onDismiss={dismissWarning}
            />
          ))}
        </div>

        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {lanes.map(([developerId, tasks]) => (
            <DeveloperLane
              key={developerId}
              developerId={developerId}
              developerLabel={developerLabels.get(developerId) ?? (developerId === 'unassigned' ? 'Unassigned' : developerId)}
              tasks={tasks}
              capacity={20}
              onDropTask={(taskId, targetDeveloperId) => {
                const sourceTask = allTasks.find((task) => task.id === taskId);
                if (!sourceTask || !effectiveSprintId) return;

                const oldValue = overrides[taskId] ?? sourceTask.assignedDevId ?? 'unassigned';
                if (oldValue === targetDeveloperId) return;

                setOverrides((state) => ({ ...state, [taskId]: targetDeveloperId }));
                const edit = {
                  field: 'assignedDevId',
                  entityId: taskId,
                  oldValue,
                  newValue: targetDeveloperId,
                };
                addEdit(edit);
                logEditMutation.mutate({ id: effectiveSprintId, edit });
              }}
              onEdit={(taskId, field, oldValue, newValue) => {
                if (!effectiveSprintId) return;
                const edit = {
                  field,
                  entityId: taskId,
                  oldValue,
                  newValue,
                };
                addEdit(edit);
                logEditMutation.mutate({ id: effectiveSprintId, edit });
              }}
              onDefer={(taskId) => {
                deferTask(taskId);
                if (!effectiveSprintId) return;
                const edit = {
                  field: 'deferred',
                  entityId: taskId,
                  oldValue: false,
                  newValue: true,
                };
                addEdit(edit);
                logEditMutation.mutate({ id: effectiveSprintId, edit });
              }}
            />
          ))}
        </div>
      </section>
    </PageLayout>
  );
}
