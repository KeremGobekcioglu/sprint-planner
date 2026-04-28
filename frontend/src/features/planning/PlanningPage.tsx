import { useEffect, useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { getMockPlanningDefaults } from '../../api/sprintApi';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { LoadingSpinner } from '../../components/shared/LoadingSpinner';
import { PageLayout } from '../../components/shared/PageLayout';
import { usePlanningStore } from '../../store/planningStore';
import type { PlanningRequest } from '../../types/planning';
import type { TeamDomain } from '../../types/team';
import { DomainSelector } from './DomainSelector';
import { EpicInput } from './EpicInput';
import { usePlanning } from './usePlanning';

interface PlanningFormValues {
  epicsText: string;
  domain: TeamDomain;
  sprintGoal: string;
  daysOff: Record<string, number>;
}

const progressSteps = [
  'Breaking down epics...',
  'Estimating points...',
  'Calculating capacity...',
  'Assigning tasks...',
];

export function PlanningPage() {
  const { teamId } = useParams();
  const navigate = useNavigate();
  const { setSprint, setTeam } = usePlanningStore();
  const { developersQuery, planMutation } = usePlanning(teamId);

  const form = useForm<PlanningFormValues>({
    defaultValues: {
      epicsText: '',
      domain: 'GENERIC',
      sprintGoal: '',
      daysOff: {},
    },
  });

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

  const loadingStep = useMemo(() => {
    if (!planMutation.isPending) {
      return null;
    }
    const elapsed = Date.now() - (planMutation.submittedAt ?? Date.now());
    const stepIndex = Math.min(Math.floor(elapsed / 3500), progressSteps.length - 1);
    return progressSteps[stepIndex];
  }, [planMutation.isPending, planMutation.submittedAt]);

  return (
    <PageLayout title="Planning" subtitle="Feed epics and constraints, then generate a sprint plan draft.">
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

            const payload: PlanningRequest = {
              teamId,
              domain: values.domain,
              sprintGoal: values.sprintGoal,
              epics,
              daysOff: values.daysOff,
            };

            setTeam(teamId);
            planMutation.mutate(payload, {
              onSuccess: (plan) => {
                setSprint(plan.sprintId);
                navigate(`/sprints/${plan.sprintId}/review`);
              },
            });
          })}
        >
          <div>
            <p className="mb-1 text-sm font-semibold text-slate-100">Epics</p>
            <EpicInput control={form.control} name="epicsText" />
          </div>

          <div>
            <p className="mb-1 text-sm font-semibold text-slate-100">Domain</p>
            <DomainSelector value={form.watch('domain')} onChange={(domain) => form.setValue('domain', domain)} />
          </div>

          <div>
            <p className="mb-1 text-sm font-semibold text-slate-100">Sprint Goal</p>
            <input
              className="w-full rounded-lg border border-slate-500/40 px-3 py-2"
              placeholder="What does success look like?"
              {...form.register('sprintGoal', { required: true })}
            />
          </div>

          <div className="space-y-2">
            <p className="text-sm font-semibold text-slate-100">Days Off by Developer</p>
            {developersQuery.isLoading ? <LoadingSpinner label="Loading developers..." /> : null}
            {developersQuery.isError ? <ErrorBanner message="Could not load developers for days-off input." /> : null}
            {developers.map((developer) => (
              <label key={developer.id} className="flex items-center justify-between gap-4 rounded-lg border border-white/10 bg-slate-950/30 p-3">
                <span className="text-sm font-medium text-slate-100">{developer.name}</span>
                <input
                  type="number"
                  className="w-24 rounded-lg border border-slate-500/40 px-2 py-1"
                  min={0}
                  {...form.register(`daysOff.${developer.id}` as const, {
                    valueAsNumber: true,
                    min: 0,
                  })}
                />
              </label>
            ))}
          </div>

          <button className="rounded-lg bg-emerald-500/85 px-4 py-2 font-semibold text-slate-950" disabled={planMutation.isPending}>
            {planMutation.isPending ? 'Generating...' : 'Generate Plan'}
          </button>
        </form>

        {planMutation.isPending && loadingStep ? <LoadingSpinner label={loadingStep} /> : null}
        {planMutation.isError ? <ErrorBanner message="Failed to generate sprint plan." /> : null}
      </section>
    </PageLayout>
  );
}
