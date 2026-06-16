import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { deleteSprint, getSprintPlanComparison, type PlanComparison, type PlanComparisonTask } from '../../api/sprintApi';
import { getTrelloStatus } from '../../api/profileApi';
import { ConfirmDialog } from '../../components/shared/ConfirmDialog';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { LoadingSpinner } from '../../components/shared/LoadingSpinner';
import { PageLayout } from '../../components/shared/PageLayout';
import { useReviewStore } from '../../store/reviewStore';
import { usePlanningStore } from '../../store/planningStore';
import type { MismatchWarning as MismatchWarningType } from '../../types/planning';
import type { Task } from '../../types/sprint';
import { ConfidenceForecast } from '../planning/ConfidenceForecast';
import { DeveloperLane } from './DeveloperLane';
import { MismatchWarning } from './MismatchWarning';
import { useReview } from './useReview';
import { HelpTooltip } from '../../components/shared/HelpTooltip';

export function ReviewPage() {
  const { sprintId } = useParams();
  const navigate = useNavigate();
  const { dismissedWarnings, dismissWarning, toggleDeferred, isDeferred, addEdit, reset } = useReviewStore();
  const { confidence } = usePlanningStore();
  const queryClient = useQueryClient();
  const { planQuery, developersQuery, teamQuery, approveMutation, pushTrelloMutation, logEditMutation } = useReview(sprintId);
  const [overrides, setOverrides] = useState<Record<string, string>>({});
  const [storyPointOverrides, setStoryPointOverrides] = useState<Record<string, number>>({});
  const [sprintPlanCollapsed, setSprintPlanCollapsed] = useState(true);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [openGuidanceTaskId, setOpenGuidanceTaskId] = useState<string | null>(null);
  const [trelloNoticeDismissed, setTrelloNoticeDismissed] = useState(
    () => localStorage.getItem('trello_notice_dismissed') === 'true',
  );
  const [planView, setPlanView] = useState<'ai' | 'final'>('final');
  const [comparison, setComparison] = useState<PlanComparison | null>(null);
  const effectiveSprintId = planQuery.data?.sprintId;

  const refreshComparison = (sprintId: string) => {
    getSprintPlanComparison(sprintId).then(setComparison);
  };

  useEffect(() => {
    if (!effectiveSprintId) return;
    refreshComparison(effectiveSprintId);
  }, [effectiveSprintId]);

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteSprint(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      navigate('/dashboard');
    },
  });

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

  const allTasks = useMemo(() =>
    [...stories.flatMap((story) => story.tasks), ...unassignedTasks].map((t) =>
      storyPointOverrides[t.id] !== undefined ? { ...t, storyPoints: storyPointOverrides[t.id]! } : t,
    ),
  [stories, unassignedTasks, storyPointOverrides]);

  const lanes = useMemo(() => {
    const laneMap = new Map<string, Task[]>();
    // Seed a lane for every known developer so lanes never disappear when emptied
    (developersQuery.data ?? []).forEach((dev) => laneMap.set(dev.id, []));
    laneMap.set('unassigned', []);
    for (const task of allTasks) {
      const effectiveOwner = overrides[task.id] ?? task.assignedDevId ?? 'unassigned';
      const laneTasks = laneMap.get(effectiveOwner) ?? [];
      laneTasks.push({ ...task, assignedDevId: effectiveOwner });
      laneMap.set(effectiveOwner, laneTasks);
    }
    return Array.from(laneMap.entries()).sort(([a], [b]) => {
      if (a === 'unassigned') return 1;
      if (b === 'unassigned') return -1;
      return 0;
    });
  }, [allTasks, overrides, developersQuery.data]);

  const epicGroups = useMemo(() => {
    const groups: Record<string, typeof stories> = {};
    for (const story of stories) {
      if (!groups[story.epicTitle]) groups[story.epicTitle] = [];
      groups[story.epicTitle]!.push(story);
    }
    return Object.entries(groups);
  }, [stories]);

  const devNameMap = useMemo(
    () => new Map((developersQuery.data ?? []).map((d) => [d.id, d.name])),
    [developersQuery.data],
  );

  const aiTaskMap = useMemo(() => {
    const map = new Map<string, PlanComparisonTask>();
    comparison?.aiPlan.forEach((t) => map.set(t.id, t));
    return map;
  }, [comparison]);

  const changedTaskIds = useMemo(
    () => new Set(comparison?.changedTaskIds ?? []),
    [comparison],
  );

  const carryoverCount = useMemo(() => {
    const plan = planQuery.data;
    const assignedCount = plan?.userStories?.flatMap((s) => s.tasks ?? [])
      .filter((t) => t.assignmentReason?.startsWith('Carry-over'))
      .length ?? 0;
    const unassignedCount = plan?.unassignedTasks
      ?.filter((t) => t.assignmentReason?.startsWith('Carry-over'))
      .length ?? 0;
    return assignedCount + unassignedCount;
  }, [planQuery.data]);

  const hasReviewedAllWarnings = warnings.every((warning) => dismissedWarnings.includes(warning.type));

  const teamId = planQuery.data?.teamId;
  const hasTrelloBoard = Boolean(teamQuery.data?.trelloBoardId);
  const isPushedToTrello =
    pushTrelloMutation.isSuccess ||
    planQuery.data?.sprintStatus === 'ACTIVE' ||
    planQuery.data?.sprintStatus === 'COMPLETED';

  const trelloStatusQuery = useQuery({
    queryKey: ['trello-status'],
    queryFn: getTrelloStatus,
    staleTime: 1000 * 60 * 5,
    retry: false,
  });

  const isTrelloConnected = trelloStatusQuery.data?.connected ?? false;
  const showTrelloNotice = Boolean(planQuery.data) && !isTrelloConnected && !trelloNoticeDismissed;

  return (
    <PageLayout
      title="Review"
      subtitle="Inspect, edit and rebalance the plan before pushing anything to Trello."
      actions={
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          {planQuery.data?.sprintStatus === 'PLANNING' && (
            <div className="flex items-center gap-1.5">
              <button
                className="min-h-[44px] w-full rounded-lg border border-red-500/40 bg-red-900/20 px-4 py-2 text-sm font-semibold text-red-400 hover:bg-red-900/30 disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"
                disabled={deleteMutation.isPending}
                onClick={() => setDeleteDialogOpen(true)}
              >
                {deleteMutation.isPending ? 'Deleting...' : 'Discard Sprint'}
              </button>
              <HelpTooltip
                text="Permanently deletes this sprint plan. You will need to run planning again."
                position="bottom"
              />
            </div>
          )}
          {isPushedToTrello ? (
            <span className="rounded-lg bg-emerald-600/20 px-4 py-2 text-sm font-semibold text-emerald-300">
              Pushed to Trello ✓
            </span>
          ) : (
            <div className="flex items-center gap-1.5">
              <HelpTooltip
                text="Approving locks the plan and pushes it to your linked Trello board. Make all edits before approving."
                position="bottom"
              />
              <span
                className="w-full sm:w-auto"
                title={
                  !isTrelloConnected
                    ? 'Connect Trello on your profile first'
                    : !hasTrelloBoard && teamQuery.data !== undefined
                      ? 'Link a Trello board in team setup first'
                      : undefined
                }
              >
              <button
                className="min-h-[44px] w-full rounded-lg bg-ink px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"
                disabled={
                  !planQuery.data ||
                  !hasReviewedAllWarnings ||
                  approveMutation.isPending ||
                  pushTrelloMutation.isPending ||
                  !effectiveSprintId ||
                  !hasTrelloBoard ||
                  !isTrelloConnected
                }
                onClick={() => {
                  if (!effectiveSprintId) return;
                  approveMutation.mutate(effectiveSprintId, {
                    onSuccess: () => {
                      pushTrelloMutation.mutate(effectiveSprintId, {
                        onSuccess: () => {
                          reset();
                          navigate(`/sprints/${effectiveSprintId}/close`, { replace: true });
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
            </span>
            </div>
          )}
        </div>
      }
    >
      <div className="mb-4">
        <button
          type="button"
          onClick={() => navigate('/dashboard')}
          className="rounded-lg border border-slate-500/40 px-3 py-1.5 text-sm font-medium text-slate-300 hover:bg-slate-800/40"
        >
          ← Back to Dashboard
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

        {confidence && <ConfidenceForecast forecast={confidence} />}

        {planQuery.data?.sprintStatus === 'PLANNING' && (!isTrelloConnected || !hasTrelloBoard) && (
          <div className="flex items-center justify-between rounded-xl border border-amber-500/30 bg-amber-900/20 px-4 py-3 text-sm">
            <span className="text-amber-200">
              ⚠ <strong>Approve &amp; Push to Trello</strong> is disabled —{' '}
              {!isTrelloConnected
                ? 'connect your Trello account on the Profile page to enable approval.'
                : 'link a Trello board to this team in Team Setup to enable approval.'}
            </span>
            <button
              type="button"
              onClick={() => navigate(!isTrelloConnected ? '/profile' : '/teams')}
              className="ml-4 shrink-0 rounded-lg bg-amber-500/85 px-3 py-1.5 text-xs font-semibold text-white hover:bg-amber-600/85"
            >
              {!isTrelloConnected ? 'Connect Trello →' : 'Set up Trello →'}
            </button>
          </div>
        )}

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
            <div className="flex w-full items-center justify-between">
              <button
                type="button"
                className="flex flex-1 items-center gap-2 text-left"
                onClick={() => setSprintPlanCollapsed((c) => !c)}
              >
                <span className="text-sm font-semibold text-slate-200">Sprint Plan</span>
                <span className="text-xs text-slate-400">{sprintPlanCollapsed ? '▶ Show' : '▼ Hide'}</span>
              </button>
              {comparison && comparison.editCount > 0 && (
                <div className="flex items-center gap-1 rounded-lg border border-white/10 bg-slate-900/60 p-0.5">
                  <button
                    type="button"
                    onClick={() => setPlanView('ai')}
                    className={`rounded px-2 py-1 text-xs font-medium transition-colors ${
                      planView === 'ai'
                        ? 'bg-blue-500/20 text-blue-300'
                        : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    AI Plan
                  </button>
                  <button
                    type="button"
                    onClick={() => setPlanView('final')}
                    className={`rounded px-2 py-1 text-xs font-medium transition-colors ${
                      planView === 'final'
                        ? 'bg-emerald-500/20 text-emerald-300'
                        : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    Final Plan
                  </button>
                </div>
              )}
            </div>
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
                            {story.tasks
                              .filter((task) => planView === 'ai' || !isDeferred(task.id))
                              .map((task) => {
                              const isChanged = changedTaskIds.has(task.id);
                              const aiTask = aiTaskMap.get(task.id);
                              const displayDevId =
                                planView === 'ai'
                                  ? (aiTask !== undefined ? aiTask.assignedDevId : task.assignedDevId)
                                  : task.assignedDevId;
                              const displayPoints =
                                planView === 'ai' ? (aiTask?.storyPoints ?? task.storyPoints) : (storyPointOverrides[task.id] ?? task.storyPoints);
                              const devName = devNameMap.get(displayDevId ?? '') ?? null;
                              return (
                                <div key={task.id} className="flex flex-wrap items-center gap-1 text-xs text-slate-400">
                                  <span>- {task.title}</span>
                                  <span className={devName ? 'text-slate-300' : 'text-red-400'}>
                                    [{devName ?? 'Unassigned'} · {task.roleTag} · {displayPoints}pts]
                                  </span>
                                  {isChanged && (
                                    <span className="rounded bg-amber-900/30 px-1 py-0.5 text-[10px] font-medium text-amber-300">
                                      edited
                                    </span>
                                  )}
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

        {deleteMutation.isError && (
          <ErrorBanner message="Failed to delete the sprint." error={deleteMutation.error} />
        )}

        {showTrelloNotice && (
          <div className="flex items-center justify-between rounded-xl border border-sky-500/30 bg-sky-900/20 px-4 py-3 text-sm">
            <span className="text-sky-200">
              🔗 Connect your Trello account to push this sprint plan directly to your board.
            </span>
            <div className="ml-4 flex shrink-0 items-center gap-2">
              <button
                type="button"
                onClick={() => navigate('/profile#trello')}
                className="rounded-lg bg-sky-500/85 px-3 py-1.5 text-xs font-semibold text-white hover:bg-sky-600/85"
              >
                Connect Trello →
              </button>
              <button
                type="button"
                onClick={() => {
                  localStorage.setItem('trello_notice_dismissed', 'true');
                  setTrelloNoticeDismissed(true);
                }}
                className="rounded-lg border border-sky-500/30 px-3 py-1.5 text-xs font-medium text-sky-300 hover:bg-sky-900/40"
              >
                Dismiss
              </button>
            </div>
          </div>
        )}

        {isPushedToTrello && (
          <div className="rounded-xl border border-amber-500/30 bg-amber-950/20 px-4 py-3">
            <p className="text-sm font-medium text-amber-300">
              🔒 This sprint has been pushed to Trello. The plan is locked — changes must be made directly on the Trello board.
            </p>
          </div>
        )}

        {carryoverCount > 0 && (
          <div className="rounded-xl border border-amber-500/30 bg-amber-950/20 px-4 py-3 flex items-start gap-3">
            <span className="text-lg">↩</span>
            <div>
              <p className="text-sm font-medium text-amber-300">
                {carryoverCount} carry-over task{carryoverCount === 1 ? '' : 's'} from the previous sprint have been pre-assigned to their original developers.
              </p>
              <p className="mt-0.5 text-xs text-amber-400/70">
                Review each carry-over task before approving — reassign or defer as needed.
              </p>
            </div>
          </div>
        )}

        {planQuery.data && allTasks.length === 0 && (
          <div className="rounded-2xl border border-white/10 bg-slate-950/40 px-6 py-12 text-center">
            <p className="text-slate-300">
              This sprint has no tasks yet. Something may have gone wrong during planning.
            </p>
            <button
              type="button"
              onClick={() => navigate(teamId ? `/teams/${teamId}/plan` : '/teams')}
              className="mt-4 rounded-lg bg-blue-500/85 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-600/85"
            >
              Back to Planning
            </button>
          </div>
        )}

        {!isPushedToTrello && (
          <p className="hidden text-xs text-slate-500 sm:block">Drag tasks between lanes to reassign developers.</p>
        )}
        <div className="grid gap-4 pb-32 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
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
              sprintId={effectiveSprintId ?? ''}
              openGuidanceTaskId={openGuidanceTaskId}
              onGuidanceToggle={setOpenGuidanceTaskId}
              readOnly={isPushedToTrello}
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
                logEditMutation.mutate({ id: effectiveSprintId, edit }, {
                  onSuccess: () => refreshComparison(effectiveSprintId),
                });
              }}
              onEdit={(taskId, field, oldValue, newValue) => {
                if (!effectiveSprintId) return;
                if (field === 'storyPoints') {
                  setStoryPointOverrides((prev) => ({ ...prev, [taskId]: newValue as number }));
                }
                const edit = {
                  field,
                  entityId: taskId,
                  oldValue,
                  newValue,
                  taskType: field === 'storyPoints' ? 'estimation' : 'UNKNOWN',
                };
                addEdit(edit);
                logEditMutation.mutate({ id: effectiveSprintId, edit }, {
                  onSuccess: () => refreshComparison(effectiveSprintId),
                });
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

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Discard Sprint"
        message="This will permanently delete the sprint plan. This cannot be undone."
        confirmLabel="Delete Sprint"
        cancelLabel="Cancel"
        destructive
        onConfirm={() => {
          setDeleteDialogOpen(false);
          if (effectiveSprintId) {
            deleteMutation.mutate(effectiveSprintId);
          }
        }}
        onCancel={() => setDeleteDialogOpen(false)}
      />
    </PageLayout>
  );
}
