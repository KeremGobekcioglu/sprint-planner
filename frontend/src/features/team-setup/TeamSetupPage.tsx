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
      <section className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
        <article className="space-y-4 rounded-3xl border border-ink/10 bg-white/80 p-5 shadow-card backdrop-blur">
          <h2 className="text-xl font-semibold text-ink">Create Team</h2>
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
              className="rounded-lg border border-ink/20 px-3 py-2"
              placeholder="Team Name"
              {...teamForm.register('name', { required: true })}
            />
            <select className="rounded-lg border border-ink/20 px-3 py-2" {...teamForm.register('domain')}>
              <option value="E_COMMERCE">E-Commerce</option>
              <option value="FINTECH">Fintech</option>
              <option value="GENERIC">Generic</option>
            </select>
            <input
              className="rounded-lg border border-ink/20 px-3 py-2"
              type="number"
              min={7}
              {...teamForm.register('sprintLengthDays', { valueAsNumber: true, min: 7 })}
            />
            <input
              className="rounded-lg border border-ink/20 px-3 py-2"
              placeholder="Trello board (optional)"
              {...teamForm.register('trelloBoardId')}
            />
            <button className="rounded-lg bg-ember px-4 py-2 font-semibold text-white sm:col-span-2">
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
                    ? 'border-ember bg-amber-50'
                    : 'border-ink/10 bg-white hover:border-ink/25 hover:bg-white/60'
                }`}
                onClick={() => setSelectedTeamId(team.id)}
              >
                <p className="font-semibold text-ink">{team.name}</p>
                <p className="text-xs text-ink/70">
                  {team.domain} • {team.sprintLengthDays} days
                </p>
              </button>
            ))}
          </div>
        </article>

        <article className="space-y-4 rounded-3xl border border-ink/10 bg-white/80 p-5 shadow-card backdrop-blur">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-semibold text-ink">Developers</h2>
            <button
              className="rounded-lg bg-ink px-3 py-2 text-sm font-semibold text-white disabled:opacity-50"
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

          {!selectedTeam ? <p className="text-sm text-ink/70">Select a team to manage developers.</p> : null}

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
                  <div key={developer.id} className="flex items-center justify-between rounded-lg border border-ink/10 p-3">
                    <div>
                      <p className="font-medium text-ink">{developer.name}</p>
                      <p className="text-xs uppercase tracking-wide text-ink/60">
                        {developer.role} • Velocity {developer.baseVelocity}
                      </p>
                    </div>
                    <button
                      className="rounded-md border border-red-300 px-3 py-1 text-sm text-red-700"
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
