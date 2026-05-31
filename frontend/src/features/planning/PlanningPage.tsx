import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { getMockPlanningDefaults, getSampleEpicsForPlanning, getPlanningMode } from '../../api/sprintApi';
import { getTeam } from '../../api/teamApi';
import { useQuery } from '@tanstack/react-query';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { LoadingSpinner } from '../../components/shared/LoadingSpinner';
import { PageLayout } from '../../components/shared/PageLayout';
import { usePlanningStore } from '../../store/planningStore';
import type { PlanningRequest } from '../../types/planning';
import type { TeamDomain } from '../../types/team';
import { DomainSelector } from './DomainSelector';
import { EpicInput } from './EpicInput';
import { usePlanning } from './usePlanning';
import { HelpTooltip } from '../../components/shared/HelpTooltip';

interface PlanningFormValues {
  epicsText: string;
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
  const { setSprint, setTeam } = usePlanningStore();
  const [isLoadingSamples, setIsLoadingSamples] = useState(false);
  const [planGenerated, setPlanGenerated] = useState(false);
  const [previousSprintContext, setPreviousSprintContext] = useState('');
  const [importOpen, setImportOpen] = useState(false);
  const { developersQuery, planMutation, planningSteps } = usePlanning(teamId);
  const planningModeQuery = useQuery({
    queryKey: ['planningMode', teamId],
    queryFn: () => getPlanningMode(teamId as string),
    enabled: Boolean(teamId),
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
      epicsText: '',
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
          epicsText: defaults.epicsText,
          domain: 'GENERIC',
          sprintGoal: defaults.sprintGoal,
          daysOff: defaults.daysOff,
        });
      })
      .catch(() => {
        // If not in mock mode or error, just skip prefill
      });
  }, [teamId, form]);

  const developers = developersQuery.data ?? [];

  return (
    <PageLayout
      title={teamQuery.data?.name ? `Sprint Planning — ${teamQuery.data.name}` : 'Sprint Planning'}
      subtitle="Feed epics and constraints, then generate a sprint plan draft."
    >
      <div className="mb-4">
        <button
          type="button"
          onClick={() => navigate('/teams')}
          className="rounded-lg border border-slate-500/40 px-3 py-1.5 text-sm font-medium text-slate-300 hover:bg-slate-800/40"
        >
          ← Back to Teams
        </button>
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

            const epics = values.epicsText
              .split('\n')
              .map((line) => line.trim())
              .filter(Boolean)
              .map((line) => {
                const [rawTitle, ...descriptionParts] = line.split('|');
                const title = (rawTitle ?? '').trim();
                return {
                  title,
                  description: descriptionParts.join('|').trim() || title,
                };
              })
              .filter((epic) => epic.title.length > 0);

const sanitizedDaysOff = Object.fromEntries(
    Object.entries(values.daysOff)
        .map(([id, val]) => [id, isNaN(val) || val == null ? 0 : val])
        .filter(([_, val]) => (val as number) > 0)  // only send non-zero
);

const payload: PlanningRequest = {
    teamId,
    domain: values.domain,
    sprintGoal: values.sprintGoal,
    epics,
    daysOff: sanitizedDaysOff,
    ...(previousSprintContext.trim() ? { previousSprintContext: previousSprintContext.trim() } : {}),
};

            setTeam(teamId);
            planMutation.mutate(payload, {
              onSuccess: (plan) => {
                setSprint(plan.sprintId);
                setPlanGenerated(true);
                setTimeout(() => navigate('/dashboard', { replace: true }), 2000);
              },
            });
          })}
        >
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

          <div>
            <div className="flex items-center justify-between">
              <p className="mb-1 text-sm font-semibold text-slate-100">Epics</p>

              <button
                type="button"
                className="rounded-lg bg-blue-500/85 px-2 py-1 text-xs font-semibold text-white hover:bg-blue-600/85 disabled:opacity-50"
                onClick={async () => {
                  if (!teamId) return;
                  setIsLoadingSamples(true);
                  try {
                    const defaults = await getSampleEpicsForPlanning(teamId);
                    form.setValue('epicsText', defaults.epicsText);
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
            <EpicInput control={form.control} name="epicsText" />
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
            className="rounded-lg bg-emerald-500/85 px-4 py-2 font-semibold text-slate-950 disabled:opacity-60"
            disabled={planMutation.isPending || planGenerated}
          >
            {planGenerated ? 'Sprint Generated ✓' : planMutation.isPending ? 'Generating...' : 'Generate Plan'}
          </button>

          {planGenerated && (
            <div className="rounded-lg border border-emerald-500/30 bg-emerald-900/20 p-3 text-sm text-emerald-300">
              Sprint plan generated successfully. You can review and approve it whenever you're ready.
            </div>
          )}
        </form>

        {planMutation.isPending && planningSteps.length === 0 ? (
          <LoadingSpinner label="Connecting to AI pipeline..." />
        ) : null}

        {planningSteps.length > 0 ? (
          <div className="mt-4 rounded-xl border border-white/10 bg-slate-950/60 p-4 font-mono text-sm">
            <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-slate-400">Pipeline Log</p>
            <ul className="space-y-1.5">
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
