import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams, Link, useBlocker } from 'react-router-dom';
import { getMockPlanningDefaults, getSampleEpicsForPlanning, getPlanningMode, getCarryoverTasks } from '../../api/sprintApi';
import { getTeam } from '../../api/teamApi';
import { useQuery } from '@tanstack/react-query';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { LoadingSpinner } from '../../components/shared/LoadingSpinner';
import { PageLayout } from '../../components/shared/PageLayout';
import { usePlanningStore } from '../../store/planningStore';
import type { CarryoverTask, Epic, PlanningRequest, SprintPlanWithId } from '../../types/planning';
import type { TeamDomain } from '../../types/team';
import { ConfidenceForecast } from './ConfidenceForecast';
import { DomainSelector } from './DomainSelector';
import { EpicInput, parsePasteText } from './EpicInput';
import { usePlanning } from './usePlanning';
import { HelpTooltip } from '../../components/shared/HelpTooltip';

interface PlanningFormValues {
  domain: TeamDomain;
  sprintGoal: string;
  daysOff: Record<string, number>;
}

const PHASE_LABEL: Record<string, string> = {
  input: 'Setup',
  memory: 'Memory',
  breakdown: 'AI Breakdown',
  estimation: 'AI Estimation',
  capacity: 'Capacity',
  distribution: 'AI Assignment',
  done: 'Done',
  error: 'Error',
};

export function PlanningPage() {
  const { teamId } = useParams();
  const navigate = useNavigate();
  const { setSprint, setTeam, setConfidence } = usePlanningStore();
  const [isLoadingSamples, setIsLoadingSamples] = useState(false);
  const [planResult, setPlanResult] = useState<SprintPlanWithId | null>(null);
  const [blockReleased, setBlockReleased] = useState(false);
  const [previousSprintContext, setPreviousSprintContext] = useState('');
  const [importOpen, setImportOpen] = useState(false);
  const [codebaseContext, setCodebaseContext] = useState('');
  const [codebaseContextCollapsed, setCodebaseContextCollapsed] = useState(true);
  const [epics, setEpics] = useState<Epic[]>([{ title: '', description: '' }]);
  const [epicError, setEpicError] = useState('');
  const [selectedCarryoverIds, setSelectedCarryoverIds] = useState<Set<string>>(new Set());
  const { developersQuery, planMutation, planningSteps } = usePlanning(teamId);
  const planningModeQuery = useQuery({
    queryKey: ['planningMode', teamId],
    queryFn: () => getPlanningMode(teamId as string),
    enabled: Boolean(teamId),
  });

  const carryoverQuery = useQuery<CarryoverTask[]>({
    queryKey: ['carryover', teamId],
    queryFn: () => getCarryoverTasks(teamId as string),
    enabled: Boolean(teamId),
    staleTime: 1000 * 60 * 5,
  });

  const teamQuery = useQuery({
    queryKey: ['team', teamId],
    queryFn: () => getTeam(teamId as string),
    enabled: Boolean(teamId),
    staleTime: Infinity,
  });

  const modeData = planningModeQuery.data as any;
  const isFresh = modeData?.mode === 'FRESH';

  const modeStep = modeData
    ? (() => {
        let message = '';
        if (isFresh) {
          message = '🆕 Fresh start — building from scratch';
        } else {
          if (modeData.hasTeamProfile) {
            message = `🧠 Continuous mode — ${modeData.completedSprints} prior sprints loaded, team preferences active`;
          } else {
            message = `📊 Continuous mode — ${modeData.completedSprints} prior sprints, preferences not yet generated`;
          }
        }
        return { phase: 'mode', message, at: new Date().toISOString(), done: true, error: false } as any;
      })()
    : null;

  const pipelineEntries = modeStep ? [modeStep, ...planningSteps] : planningSteps;

  const form = useForm<PlanningFormValues>({
    defaultValues: {
      domain: 'GENERIC',
      sprintGoal: '',
      daysOff: {},
    },
  });

  useEffect(() => {
    if (teamQuery.data?.domain) {
      form.setValue('domain', teamQuery.data.domain as TeamDomain);
    }
  }, [teamQuery.data?.domain, form]);

  useEffect(() => {
    if (!teamId) return;

    getMockPlanningDefaults(teamId)
      .then((defaults) => {
        form.reset({
          domain: 'GENERIC',
          sprintGoal: defaults.sprintGoal,
          daysOff: defaults.daysOff,
        });
        if (defaults.epicsText) {
          setEpics(parsePasteText(defaults.epicsText));
        }
      })
      .catch(() => {
        // If not in mock mode or error, just skip prefill
      });
  }, [teamId, form]);

  const developers = developersQuery.data ?? [];
  const carryoverTasks = carryoverQuery.data ?? [];

  // Pre-select all carryover tasks when they load
  useEffect(() => {
    if (carryoverTasks.length > 0) {
      setSelectedCarryoverIds(new Set(carryoverTasks.map((t) => t.taskId)));
    }
  }, [carryoverTasks.length]); // eslint-disable-line react-hooks/exhaustive-deps

  // Navigation block while AI pipeline is running
  const shouldBlock = planMutation.isPending && !blockReleased;
  const blocker = useBlocker(shouldBlock);

  useEffect(() => {
    if (blocker.state === 'blocked') blocker.reset();
  }, [blocker]);

  // Auto-release block after 90s so user isn't permanently stuck on hang
  useEffect(() => {
    if (!planMutation.isPending) { setBlockReleased(false); return; }
    const t = setTimeout(() => setBlockReleased(true), 90_000);
    return () => clearTimeout(t);
  }, [planMutation.isPending]);

  useEffect(() => {
    if (planMutation.isError) setBlockReleased(true);
  }, [planMutation.isError]);

  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (planMutation.isPending && !blockReleased) e.preventDefault();
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [planMutation.isPending, blockReleased]);

  // Result view — shown after successful generation instead of auto-redirect
  if (planResult) {
    const totalTasks = planResult.userStories.reduce((acc, s) => acc + s.tasks.length, 0);
    const totalPoints = planResult.userStories.reduce((acc, s) => acc + s.storyPoints, 0);
    return (
      <PageLayout
        title={teamQuery.data?.name ? `Sprint Planning — ${teamQuery.data.name}` : 'Sprint Planning'}
        subtitle="Sprint plan generated successfully."
      >
        <div className="mx-auto max-w-2xl space-y-5">
          <div className="rounded-2xl border border-emerald-500/30 bg-emerald-950/20 p-5">
            <div className="flex items-center gap-3">
              <span className="text-2xl">✅</span>
              <div>
                <h2 className="font-semibold text-slate-100">Sprint plan generated</h2>
                <p className="text-sm text-slate-400">
                  {totalTasks} task{totalTasks !== 1 ? 's' : ''} across{' '}
                  {planResult.userStories.length} user stor{planResult.userStories.length !== 1 ? 'ies' : 'y'}{' '}
                  · {totalPoints} total points
                </p>
              </div>
            </div>
          </div>

          {planningSteps.length > 0 && (
            <div className="rounded-xl border border-white/10 bg-slate-950/60 p-4 font-mono text-xs">
              <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-slate-400">Pipeline Log</p>
              <ul className="max-h-48 space-y-1 overflow-y-auto">
                {(modeStep ? [modeStep, ...planningSteps] : planningSteps).map((step, i) => (
                  <li key={i} className="flex items-start gap-2">
                    <span className={step.error ? 'text-red-400' : step.done ? 'text-emerald-400' : 'text-sky-400'}>
                      {step.error ? '✗' : step.done ? '✓' : '›'}
                    </span>
                    <span className="text-slate-300">
                      <span className="text-slate-500">[{PHASE_LABEL[step.phase] ?? step.phase}]</span>{' '}
                      {step.message}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="rounded-2xl border border-slate-700 bg-slate-900/60 p-5 space-y-3">
            <h3 className="font-semibold text-slate-200">What the AI applied</h3>
            <div className="space-y-2 text-sm text-slate-400">
              {modeData?.mode === 'CONTINUOUS' && modeData.hasTeamProfile && (
                <div className="flex items-start gap-2">
                  <span className="text-emerald-400 mt-0.5">✓</span>
                  <span>Team preferences from {modeData.completedSprints} previous sprint{modeData.completedSprints === 1 ? '' : 's'} applied</span>
                </div>
              )}
              {modeData?.mode === 'CONTINUOUS' && modeData.hasSprintHistory && (
                <div className="flex items-start gap-2">
                  <span className="text-emerald-400 mt-0.5">✓</span>
                  <span>Sprint history used for estimation and capacity decisions</span>
                </div>
              )}
              {modeData?.mode === 'FRESH' && (
                <div className="flex items-start gap-2">
                  <span className="text-blue-400 mt-0.5">ℹ</span>
                  <span>Fresh start — conservative defaults used. Memory builds after your first sprint review.</span>
                </div>
              )}
              {(planResult.unassignedTasks?.length ?? 0) > 0 && (
                <div className="flex items-start gap-2">
                  <span className="text-amber-400 mt-0.5">⚠️</span>
                  <span>
                    {planResult.unassignedTasks!.length} task{planResult.unassignedTasks!.length === 1 ? '' : 's'} unassigned —
                    no developer with matching capability had remaining capacity.
                    You can assign them manually on the Review screen.
                  </span>
                </div>
              )}
              <div className="flex items-start gap-2">
                <span className="text-slate-500 mt-0.5">→</span>
                <span>Capacity-adjusted plan: each developer assigned within their sprint velocity</span>
              </div>
            </div>
          </div>

          {planResult.confidence && <ConfidenceForecast forecast={planResult.confidence} />}

          <div className="flex flex-col gap-3 sm:flex-row">
            <button
              onClick={() => navigate(`/sprints/${planResult.sprintId}/review`, { replace: true })}
              className="flex-1 rounded-xl bg-emerald-500 px-6 py-3 font-semibold text-slate-950 hover:bg-emerald-400 transition-colors"
            >
              Review Plan →
            </button>
            <button
              onClick={() => navigate('/dashboard', { replace: true })}
              className="flex-1 rounded-xl border border-white/10 px-6 py-3 font-semibold text-slate-300 hover:bg-slate-800/40 transition-colors"
            >
              Go to Dashboard
            </button>
          </div>

          <p className="text-center text-xs text-slate-500">
            Your sprint plan is saved. You can also access it from the Dashboard at any time.
          </p>
        </div>
      </PageLayout>
    );
  }

  return (
    <PageLayout
      title={teamQuery.data?.name ? `Sprint Planning — ${teamQuery.data.name}` : 'Sprint Planning'}
      subtitle="Feed epics and constraints, then generate a sprint plan draft."
    >
      <div className="mb-4 flex items-center gap-3">
        <button
          type="button"
          onClick={() => navigate('/teams')}
          disabled={shouldBlock}
          className="rounded-lg border border-slate-500/40 px-3 py-1.5 text-sm font-medium text-slate-300 hover:bg-slate-800/40 disabled:cursor-not-allowed disabled:opacity-40"
        >
          ← Back to Teams
        </button>
        {shouldBlock && (
          <span className="text-xs text-amber-400">
            AI pipeline running — navigation paused (auto-releases after 90s)
          </span>
        )}
      </div>

      {/* Mode info panel */}
      {modeData ? (
        isFresh ? (
          <div className="mb-4 rounded-xl border border-blue-500/30 bg-blue-950/30 p-4">
            <div className="mb-1 flex items-center gap-2">
              <span>🌱</span>
              <span className="font-semibold text-blue-200">Fresh Start</span>
              <HelpTooltip
                text="First sprint with this team. The AI uses conservative defaults and will learn from your edits."
                position="right"
              />
            </div>
            <p className="text-sm text-blue-300/80">
              This team has no sprint history. The AI will use conservative defaults for this sprint.
              Memory will start building after your first completed sprint review.
            </p>
          </div>
        ) : (
          <div className="mb-4 rounded-xl border border-teal-500/30 bg-teal-950/30 p-4">
            <div className="mb-1 flex items-center gap-2">
              <span>🧠</span>
              <span className="font-semibold text-teal-200">
                Continuous Mode — {modeData.completedSprints} sprints completed
              </span>
              <HelpTooltip
                text="The AI has learned from past sprints and will apply your team's preferences automatically."
                position="right"
              />
            </div>
            <p className="mb-2 text-sm text-teal-300/80">
              The AI will use your team's sprint history and learned preferences for this plan.
            </p>
            <Link to="/profile#memory" className="text-sm text-teal-400 hover:underline">
              View Memory →
            </Link>
          </div>
        )
      ) : null}

      <section className="grid gap-6 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card">
        <form
          className="space-y-4"
          onSubmit={form.handleSubmit((values) => {
            if (!teamId) {
              return;
            }

            const validEpics = epics.filter((e) => e.title.trim().length > 0);
            if (validEpics.length === 0) {
              setEpicError('At least one epic with a title is required.');
              return;
            }
            setEpicError('');

const sanitizedDaysOff = Object.fromEntries(
    Object.entries(values.daysOff)
        .map(([id, val]) => [id, isNaN(val) || val == null ? 0 : val])
        .filter(([_, val]) => (val as number) > 0)  // only send non-zero
);

const selectedCarryovers = carryoverTasks.filter((t) => selectedCarryoverIds.has(t.taskId));

const payload: PlanningRequest = {
    teamId,
    domain: values.domain,
    sprintGoal: values.sprintGoal,
    epics: validEpics,
    daysOff: sanitizedDaysOff,
    ...(previousSprintContext.trim() ? { previousSprintContext: previousSprintContext.trim() } : {}),
    ...(selectedCarryovers.length > 0 ? { carryoverTasks: selectedCarryovers } : {}),
    ...(codebaseContext.trim() ? { codebaseContext: codebaseContext.trim() } : {}),
};

            setTeam(teamId);
            planMutation.mutate(payload, {
              onSuccess: (plan) => {
                setSprint(plan.sprintId);
                setConfidence(plan.confidence ?? null);
                setPlanResult(plan);
              },
            });
          })}
        >
          {/* Carry-over panel — shown when previous sprint has NOT_DONE tasks */}
          {carryoverTasks.length > 0 && (
            <div className="rounded-xl border border-violet-500/30 bg-violet-950/20 p-4">
              <div className="mb-3 flex items-center justify-between">
                <div>
                  <p className="font-semibold text-violet-200">↩ Carry-over from previous sprint</p>
                  <p className="mt-0.5 text-xs text-violet-400">"{carryoverTasks[0]?.sprintGoal}"</p>
                </div>
                <div className="flex gap-2">
                  <button
                    type="button"
                    className="rounded-md border border-violet-500/40 px-2 py-1 text-xs text-violet-300 hover:bg-violet-800/30"
                    onClick={() => setSelectedCarryoverIds(new Set(carryoverTasks.map((t) => t.taskId)))}
                  >
                    Select all
                  </button>
                  <button
                    type="button"
                    className="rounded-md border border-violet-500/40 px-2 py-1 text-xs text-violet-300 hover:bg-violet-800/30"
                    onClick={() => setSelectedCarryoverIds(new Set())}
                  >
                    Deselect all
                  </button>
                </div>
              </div>
              <div className="space-y-2">
                {carryoverTasks.map((task) => (
                  <label key={task.taskId} className="flex cursor-pointer items-start gap-3 rounded-lg border border-violet-500/20 bg-violet-950/30 p-2.5 hover:bg-violet-900/20">
                    <input
                      type="checkbox"
                      className="mt-0.5 h-4 w-4 accent-violet-500"
                      checked={selectedCarryoverIds.has(task.taskId)}
                      onChange={(e) => {
                        setSelectedCarryoverIds((prev) => {
                          const next = new Set(prev);
                          if (e.target.checked) next.add(task.taskId);
                          else next.delete(task.taskId);
                          return next;
                        });
                      }}
                    />
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-violet-100">{task.taskTitle}</p>
                      <p className="mt-0.5 text-xs text-violet-400">
                        {task.roleTag} · {task.storyPoints}pts ·{' '}
                        {task.previousAssignedDevName ? `was assigned to ${task.previousAssignedDevName}` : 'was unassigned'}
                      </p>
                    </div>
                  </label>
                ))}
              </div>
            </div>
          )}

          {/* Import previous sprint history — only in Fresh Start mode */}
          {isFresh ? (
            <div className="overflow-hidden rounded-xl border border-slate-600/40">
              <button
                type="button"
                onClick={() => setImportOpen(!importOpen)}
                className="flex w-full items-center justify-between px-4 py-3 text-sm font-medium text-slate-300 hover:bg-slate-800/40"
              >
                <span>Import Previous Sprint History (Optional)</span>
                <span className="text-slate-500">{importOpen ? '▲' : '▼'}</span>
              </button>
              {importOpen ? (
                <div className="space-y-3 border-t border-slate-600/40 px-4 pb-4 pt-3">
                  <textarea
                    value={previousSprintContext}
                    onChange={(e) => setPreviousSprintContext(e.target.value)}
                    placeholder={`Paste your previous sprint summary here.\n\nFormat: Sprint goal, what was completed, velocity, any blockers.\n\nExample:\nSprint goal: Launch checkout flow\nVelocity: 34/40 pts completed\nBlockers: Payment integration took longer than estimated`}
                    rows={7}
                    className="w-full resize-y rounded-lg border border-slate-500/40 bg-slate-950/60 px-3 py-2 text-sm text-slate-200 placeholder-slate-600"
                  />
                  <div>
                    <label className="mb-1 block text-xs text-slate-400">Or upload a .txt / .md file</label>
                    <input
                      type="file"
                      accept=".txt,.md"
                      className="text-sm text-slate-400"
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (!file) return;
                        const reader = new FileReader();
                        reader.onload = (evt) => {
                          setPreviousSprintContext((evt.target?.result as string) ?? '');
                        };
                        reader.readAsText(file);
                      }}
                    />
                  </div>
                </div>
              ) : null}
            </div>
          ) : null}

          {/* Codebase Context (optional) */}
          <div className="overflow-hidden rounded-xl border border-slate-600/40">
            <button
              type="button"
              onClick={() => setCodebaseContextCollapsed(!codebaseContextCollapsed)}
              className="flex w-full items-center justify-between px-4 py-3 text-sm font-medium text-slate-300 hover:bg-slate-800/40"
            >
              <span>📁 Add codebase context</span>
              <span className="text-slate-500">{codebaseContextCollapsed ? '▼' : '▲'}</span>
            </button>
            {!codebaseContextCollapsed && (
              <div className="space-y-2 border-t border-slate-600/40 px-4 pb-4 pt-3">
                <p className="text-xs text-slate-400">
                  Describe your tech stack and existing modules to help the AI generate
                  accurate, non-redundant tasks.
                </p>
                <textarea
                  className="w-full rounded-lg border border-slate-500/40 bg-slate-950/60 px-3 py-2 text-sm text-slate-100 placeholder-slate-500 min-h-[100px]"
                  placeholder={`Stack: React, Spring Boot, PostgreSQL\nExisting: auth complete, team CRUD done\nIn progress: payment flow 50% done\nConventions: REST APIs, constructor injection\nConstraints: must support mobile browsers`}
                  value={codebaseContext}
                  onChange={(e) => setCodebaseContext(e.target.value)}
                />
              </div>
            )}
          </div>

          <div>
            <div className="mb-2 flex items-center justify-between">
              <p className="text-sm font-semibold text-slate-100">Epics</p>

              <button
                type="button"
                className="min-h-[44px] rounded-lg bg-blue-500/85 px-3 py-1 text-xs font-semibold text-white hover:bg-blue-600/85 disabled:opacity-50"
                onClick={async () => {
                  if (!teamId) return;
                  setIsLoadingSamples(true);
                  try {
                    const defaults = await getSampleEpicsForPlanning(teamId);
                    if (defaults.epicsText) {
                      setEpics(parsePasteText(defaults.epicsText));
                    }
                    form.setValue('sprintGoal', defaults.sprintGoal);
                  } finally {
                    setIsLoadingSamples(false);
                  }
                }}
                disabled={isLoadingSamples}
                title="Load sample epics for this team"
              >
                {isLoadingSamples ? 'Loading...' : '⚡ Load Samples'}
              </button>
            </div>
            <EpicInput epics={epics} onChange={setEpics} />
            {epicError && <p className="mt-1 text-xs text-red-400">{epicError}</p>}
          </div>

          <div>
            <div className="mb-1 flex items-center gap-1.5">
              <p className="text-sm font-semibold text-slate-100">Domain</p>
              <HelpTooltip
                text="The AI uses domain-specific rules. Fintech adds compliance tasks, E-Commerce adds analytics and A/B test tasks."
                position="right"
              />
            </div>
            <DomainSelector value={form.watch('domain')} onChange={(domain) => form.setValue('domain', domain)} />
          </div>

          <div>
            <div className="mb-1 flex items-center gap-1.5">
              <p className="text-sm font-semibold text-slate-100">Sprint Goal</p>
              <HelpTooltip
                text="A one-sentence description of what this sprint should achieve. The AI uses this to focus task generation."
                position="right"
              />
            </div>
            <input
              className="w-full rounded-lg border border-slate-500/40 px-3 py-2"
              placeholder="What does success look like?"
              {...form.register('sprintGoal', { required: true })}
            />
          </div>

          <div className="space-y-2">
            <div className="flex items-center gap-1.5">
              <p className="text-sm font-semibold text-slate-100">Days Off by Developer</p>
              <HelpTooltip
                text="Mark how many days each developer is unavailable. Reduces their capacity for this sprint."
                position="right"
              />
            </div>
            {developersQuery.isLoading ? <LoadingSpinner label="Loading developers..." /> : null}
            {developersQuery.isError ? <ErrorBanner message="Could not load developers for days-off input." /> : null}
            {developers.map((developer) => (
              <label key={developer.id} className="flex items-center justify-between gap-4 rounded-lg border border-white/10 bg-slate-950/30 p-3">
                <span className="text-sm font-medium text-slate-100">{developer.name}</span>
                <div className="flex items-center gap-2">
                  <span className="flex items-center gap-1 text-xs text-slate-500">
                    {developer.baseVelocity} pts/sprint
                    <HelpTooltip
                      text="Story points this developer can complete in one sprint. Used to calculate capacity."
                      position="top"
                    />
                  </span>
                  <input
                    type="number"
                    className="w-24 rounded-lg border border-slate-500/40 px-2 py-1"
                    min={0}
                    {...form.register(`daysOff.${developer.id}` as const, {
                      valueAsNumber: true,
                      min: 0,
                    })}
                  />
                </div>
              </label>
            ))}
          </div>

          <button
            className="w-full rounded-lg bg-emerald-500/85 px-4 py-2 font-semibold text-slate-950 disabled:opacity-60 sm:w-auto"
            disabled={planMutation.isPending}
          >
            {planMutation.isPending ? 'Generating...' : 'Generate Plan'}
          </button>
        </form>

        {planMutation.isPending && planningSteps.length === 0 ? (
          <LoadingSpinner label="Connecting to AI pipeline..." />
        ) : null}

        {planningSteps.length > 0 ? (
          <div className="mt-4 rounded-xl border border-white/10 bg-slate-950/60 p-4 font-mono text-xs sm:text-sm">
            <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-slate-400">Pipeline Log</p>
            <ul className="max-h-64 space-y-1.5 overflow-y-auto">
              {pipelineEntries.map((step, i) => (
                <li key={i} className="flex items-start gap-2">
                  <span className={
                    step.error
                      ? 'text-red-400'
                      : step.done
                        ? 'text-emerald-400'
                        : 'text-sky-400'
                  }>
                    {step.error ? '✗' : step.done ? '✓' : '›'}
                  </span>
                  <span className="text-slate-300">
                    <span className="text-slate-500">[{PHASE_LABEL[step.phase] ?? step.phase}]</span>{' '}
                    {step.message}
                  </span>
                </li>
              ))}
              {planMutation.isPending ? (
                <li className="flex items-center gap-2 text-slate-500">
                  <span className="animate-pulse">›</span>
                  <span>Working...</span>
                </li>
              ) : null}
            </ul>
          </div>
        ) : null}

        {planMutation.isError ? <ErrorBanner message="Failed to generate sprint plan." error={planMutation.error} /> : null}
      </section>
    </PageLayout>
  );
}
