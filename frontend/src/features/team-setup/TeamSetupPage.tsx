import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { LoadingSpinner } from '../../components/shared/LoadingSpinner';
import { PageLayout } from '../../components/shared/PageLayout';
import { usePlanningStore } from '../../store/planningStore';
import type { Team } from '../../types/team';
import { DeveloperForm } from './DeveloperForm';
import { useTeamSetup } from './useTeamSetup';

interface TeamFormValues {
  name: string;
  domain: Team['domain'];
  sprintLengthDays: number;
  trelloBoardId?: string;
}

export function TeamSetupPage() {
  const navigate = useNavigate();
  const { setTeam } = usePlanningStore();
  const [selectedTeamId, setSelectedTeamId] = useState<string | null>(null);
  const { teamsQuery, developersQuery, createTeamMutation, createDeveloperMutation, removeDeveloperMutation } =
    useTeamSetup(selectedTeamId);

  const teamForm = useForm<TeamFormValues>({
    defaultValues: {
      name: '',
      domain: 'GENERIC',
      sprintLengthDays: 14,
      trelloBoardId: '',
    },
  });

  const selectedTeam = useMemo(
    () => teamsQuery.data?.find((team) => team.id === selectedTeamId),
    [teamsQuery.data, selectedTeamId],
  );

  return (
    <PageLayout title="Teams" subtitle="Create your team, tune capacity and move into sprint planning.">
      <section className="grid gap-6">
        <article className="space-y-4 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card backdrop-blur-xl">
          <h2 className="text-xl font-semibold text-slate-100">Create Team</h2>
          <form
            className="grid gap-3 sm:grid-cols-2"
            onSubmit={teamForm.handleSubmit((values) => {
              createTeamMutation.mutate(values, {
                onSuccess: (team) => {
                  setSelectedTeamId(team.id);
                  teamForm.reset();
                },
              });
            })}
          >
            <input
              className="rounded-lg border border-slate-500/40 px-3 py-2"
              placeholder="Team Name"
              {...teamForm.register('name', { required: true })}
            />
            <select className="rounded-lg border border-slate-500/40 px-3 py-2" {...teamForm.register('domain')}>
              <option value="E_COMMERCE">E-Commerce</option>
              <option value="FINTECH">Fintech</option>
              <option value="GENERIC">Generic</option>
            </select>
            <input
              className="rounded-lg border border-slate-500/40 px-3 py-2"
              type="number"
              min={7}
              {...teamForm.register('sprintLengthDays', { valueAsNumber: true, min: 7 })}
            />
            <input
              className="rounded-lg border border-slate-500/40 px-3 py-2"
              placeholder="Trello board (optional)"
              {...teamForm.register('trelloBoardId')}
            />
            <button className="rounded-lg bg-emerald-500/85 px-4 py-2 font-semibold text-slate-950 shadow-lg shadow-emerald-900/30 sm:col-span-2">
              {createTeamMutation.isPending ? 'Creating...' : 'Create Team'}
            </button>
          </form>

          {teamsQuery.isLoading ? <LoadingSpinner label="Loading teams..." /> : null}
          {teamsQuery.isError ? <ErrorBanner message="Unable to fetch teams." /> : null}

          <div className="grid gap-3">
            {teamsQuery.data?.map((team) => (
              <button
                key={team.id}
                className={`rounded-xl border p-4 text-left transition ${
                  selectedTeamId === team.id
                    ? 'border-emerald-300/60 bg-emerald-400/12'
                    : 'border-white/10 bg-slate-950/45 hover:border-slate-300/30 hover:bg-slate-900/55'
                }`}
                onClick={() => setSelectedTeamId(team.id)}
              >
                <p className="font-semibold text-slate-100">{team.name}</p>
                <p className="text-xs text-slate-300">
                  {team.domain} • {team.sprintLengthDays} days
                </p>
              </button>
            ))}
          </div>
        </article>

        <article className="space-y-4 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card backdrop-blur-xl">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-semibold text-slate-100">Developers</h2>
            <button
              className="rounded-lg bg-slate-200/90 px-3 py-2 text-sm font-semibold text-slate-900 disabled:opacity-50"
              disabled={!selectedTeam}
              onClick={() => {
                if (!selectedTeam) return;
                setTeam(selectedTeam.id);
                navigate(`/teams/${selectedTeam.id}/plan`);
              }}
            >
              Go to Planning
            </button>
          </div>

          {!selectedTeam ? <p className="text-sm text-slate-300/85">Select a team to manage developers.</p> : null}

          {selectedTeam ? (
            <>
              <DeveloperForm
                isSubmitting={createDeveloperMutation.isPending}
                onSubmit={(values) => {
                  createDeveloperMutation.mutate({ teamId: selectedTeam.id, payload: values });
                }}
              />

              {developersQuery.isLoading ? <LoadingSpinner label="Loading developers..." /> : null}
              {developersQuery.isError ? <ErrorBanner message="Unable to load developers for this team." /> : null}

              <div className="space-y-2">
                {developersQuery.data?.map((developer) => (
                  <div
                    key={developer.id}
                    className="flex items-center justify-between rounded-lg border border-white/10 bg-slate-950/45 p-3"
                  >
                    <div>
                      <p className="font-medium text-slate-100">{developer.name}</p>
                      <p className="text-xs uppercase tracking-wide text-slate-300">
                        {developer.role} • {developer.capabilities?.join(', ') || 'no capabilities'} • Velocity {developer.baseVelocity}
                      </p>
                    </div>
                    <button
                      className="rounded-md border border-rose-300/45 bg-rose-400/10 px-3 py-1 text-sm text-rose-200"
                      onClick={() =>
                        removeDeveloperMutation.mutate({ teamId: selectedTeam.id, developerId: developer.id })
                      }
                    >
                      Remove
                    </button>
                  </div>
                ))}
              </div>
            </>
          ) : null}
        </article>
      </section>
    </PageLayout>
  );
}
