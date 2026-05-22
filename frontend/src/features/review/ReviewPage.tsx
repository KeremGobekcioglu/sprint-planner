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
  const { dismissedWarnings, dismissWarning, toggleDeferred, isDeferred, addEdit, reset } = useReviewStore();
  const { planQuery, developersQuery, approveMutation, pushTrelloMutation, logEditMutation } = useReview(sprintId);
  const [overrides, setOverrides] = useState<Record<string, string>>({});
  const [sprintPlanCollapsed, setSprintPlanCollapsed] = useState(true);
  const effectiveSprintId = planQuery.data?.sprintId;

  const stories = planQuery.data?.userStories ?? [];
  const unassignedTasks = planQuery.data?.unassignedTasks ?? [];
  const warnings: MismatchWarningType[] = [];
  const developerLabels = useMemo(() => {
    return new Map(
      (developersQuery.data ?? []).map((developer) => [developer.id, `${developer.name} • ${developer.role}`]),
    );
  }, [developersQuery.data]);

  const capacityMap = useMemo(() => {
    const map = new Map<string, number>();
    (developersQuery.data ?? []).forEach((dev) => map.set(dev.id, dev.baseVelocity));
    return map;
  }, [developersQuery.data]);

  const allTasks = useMemo(() => [...stories.flatMap((story) => story.tasks), ...unassignedTasks], [stories, unassignedTasks]);

  const lanes = useMemo(() => {
    const laneMap = new Map<string, Task[]>();
    for (const task of allTasks) {
      const effectiveOwner = overrides[task.id] ?? task.assignedDevId ?? 'unassigned';
      const laneTasks = laneMap.get(effectiveOwner) ?? [];
      laneTasks.push({ ...task, assignedDevId: effectiveOwner });
      laneMap.set(effectiveOwner, laneTasks);
    }
    // Always keep the unassigned lane when plan data is loaded so tasks can be dragged back
    if (allTasks.length > 0 && !laneMap.has('unassigned')) {
      laneMap.set('unassigned', []);
    }
    return Array.from(laneMap.entries()).sort(([a], [b]) => {
      if (a === 'unassigned') return 1;
      if (b === 'unassigned') return -1;
      return 0;
    });
  }, [allTasks, overrides]);

  const epicGroups = useMemo(() => {
    const groups: Record<string, typeof stories> = {};
    for (const story of stories) {
      if (!groups[story.epicTitle]) groups[story.epicTitle] = [];
      groups[story.epicTitle].push(story);
    }
    return Object.entries(groups);
  }, [stories]);

  const devNameMap = useMemo(
    () => new Map((developersQuery.data ?? []).map((d) => [d.id, d.name])),
    [developersQuery.data],
  );

  const hasReviewedAllWarnings = warnings.every((warning) => dismissedWarnings.includes(warning.type));

  const teamId = planQuery.data?.teamId;

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
      <div className="mb-4">
        <button
          type="button"
          onClick={() => navigate(teamId ? `/teams/${teamId}/plan` : '/teams')}
          className="rounded-lg border border-slate-500/40 px-3 py-1.5 text-sm font-medium text-slate-300 hover:bg-slate-800/40"
        >
          ← Back to Planning
        </button>
      </div>
      <section className="space-y-5">
        {planQuery.isLoading ? <LoadingSpinner label="Loading sprint review..." /> : null}
        {planQuery.isError ? <ErrorBanner message="Could not load the sprint plan." /> : null}
        {approveMutation.isError ? (
          <ErrorBanner message="Failed to approve the plan." error={approveMutation.error} />
        ) : null}
        {pushTrelloMutation.isError ? (
          <ErrorBanner message="Failed to push plan to Trello." error={pushTrelloMutation.error} />
        ) : null}
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

        {planQuery.data && epicGroups.length > 0 && (
          <div className="rounded-2xl border border-white/10 bg-slate-950/60 p-4">
            <button
              type="button"
              className="flex w-full items-center justify-between text-left"
              onClick={() => setSprintPlanCollapsed((c) => !c)}
            >
              <span className="text-sm font-semibold text-slate-200">Sprint Plan</span>
              <span className="text-xs text-slate-400">{sprintPlanCollapsed ? '▶ Show' : '▼ Hide'}</span>
            </button>
            {!sprintPlanCollapsed && (
              <div className="mt-4 space-y-4">
                {epicGroups.map(([epicTitle, epicStories]) => (
                  <div key={epicTitle}>
                    <p className="mb-2 text-sm font-bold text-slate-100">▼ Epic: {epicTitle}</p>
                    <div className="space-y-3 pl-3">
                      {epicStories.map((story) => (
                        <div key={story.id}>
                          <p className="mb-1 text-xs font-semibold text-slate-300">
                            ▼ {story.title}{' '}
                            <span className="font-normal text-slate-500">
                              ({story.storyPoints} pts · {story.status})
                            </span>
                          </p>
                          <div className="space-y-0.5 pl-3">
                            {story.tasks.map((task) => {
                              const devName = devNameMap.get(task.assignedDevId ?? '') ?? null;
                              return (
                                <div key={task.id} className="flex flex-wrap items-center gap-1 text-xs text-slate-400">
                                  <span>- {task.title}</span>
                                  <span className={devName ? 'text-slate-300' : 'text-red-400'}>
                                    [{devName ?? 'Unassigned'} · {task.roleTag} · {task.storyPoints}pts]
                                  </span>
                                </div>
                              );
                            })}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        <div className="grid gap-4 pb-32 md:grid-cols-2 xl:grid-cols-3">
          {lanes.map(([developerId, tasks]) => (
            <DeveloperLane
              key={developerId}
              developerId={developerId}
              developerLabel={developerLabels.get(developerId) ?? (developerId === 'unassigned' ? 'Unassigned' : developerId)}
              tasks={tasks}
              stories={planQuery.data?.userStories ?? []}
              capacity={
                capacityMap.get(developerId) ??
                (developersQuery.data ?? []).find((d) => d.id === developerId)?.baseVelocity ??
                20
              }
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
                  taskType: 'assignment',
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
                  taskType: field === 'storyPoints' ? 'estimation' : 'UNKNOWN',
                };
                addEdit(edit);
                logEditMutation.mutate({ id: effectiveSprintId, edit });
              }}
              onDefer={(taskId) => {
                const currentDeferred = isDeferred(taskId);
                toggleDeferred(taskId);
                if (!effectiveSprintId) return;
                const edit = {
                  field: 'deferred',
                  entityId: taskId,
                  oldValue: currentDeferred,
                  newValue: !currentDeferred,
                  taskType: 'deferred',
                };
                addEdit(edit);
                logEditMutation.mutate({ id: effectiveSprintId, edit });
              }}
              isDeferred={isDeferred}
            />
          ))}
        </div>
      </section>
    </PageLayout>
  );
}
