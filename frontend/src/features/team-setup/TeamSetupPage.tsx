import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { LoadingSpinner } from '../../components/shared/LoadingSpinner';
import { NoticeBanner } from '../../components/shared/NoticeBanner';
import { PageLayout } from '../../components/shared/PageLayout';
import { usePlanningStore } from '../../store/planningStore';
import type { Team } from '../../types/team';
import { createDeveloper, createTeam, getTeams, loadSampleData } from '../../api/teamApi';
import { getActiveSprint } from '../../api/sprintApi';
import { SAMPLE_DEVELOPERS, SAMPLE_TEAMS } from '../../api/sampleData';
import { DeveloperForm } from './DeveloperForm';
import { useTeamSetup } from './useTeamSetup';

const USE_REAL_API = import.meta.env.VITE_USE_REAL_API === 'true';

interface TeamFormValues {
  name: string;
  domain: Team['domain'];
  sprintLengthDays: number;
  trelloBoardId?: string;
}

export function TeamSetupPage() {
  const navigate = useNavigate();
  const { setTeam } = usePlanningStore();
  const queryClient = useQueryClient();
  const [selectedTeamId, setSelectedTeamId] = useState<string | null>(null);
  const [isLoadingSamples, setIsLoadingSamples] = useState(false);
  const { teamsQuery, developersQuery, createTeamMutation, createDeveloperMutation, removeDeveloperMutation } =
    useTeamSetup(selectedTeamId);

  const activeSprintQuery = useQuery({
    queryKey: ['active-sprint'],
    queryFn: getActiveSprint,
    staleTime: 1000 * 60 * 2,
    retry: false,
  });

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

  async function handleLoadSamples() {
    setIsLoadingSamples(true);
    try {
      const result = await loadSampleData();
      await queryClient.invalidateQueries({ queryKey: ['teams'] });
      await queryClient.invalidateQueries({ queryKey: ['developers'] });

      const refreshedTeams = await queryClient.fetchQuery({ queryKey: ['teams'], queryFn: getTeams });
      const nextSelected =
        refreshedTeams.find((team) => team.id === result.firstTeamId) ?? refreshedTeams[0] ?? null;

      setSelectedTeamId(nextSelected?.id ?? null);
      if (nextSelected) {
        setTeam(nextSelected.id);
        await queryClient.invalidateQueries({ queryKey: ['developers', nextSelected.id] });
      }

      console.log('Sample data loaded successfully', result);
    } catch (error) {
      console.error('Error loading sample data:', error);
    } finally {
      setIsLoadingSamples(false);
    }
  }

  return (
    <PageLayout title="Teams" subtitle="Create your team, tune capacity and move into sprint planning.">
      {activeSprintQuery.data ? (
        <div className="mb-4">
          <NoticeBanner
            title="You have an active sprint"
            message={`Sprint ${activeSprintQuery.data.sprintId.slice(0, 8)}… is currently ${activeSprintQuery.data.status.toLowerCase().replace('_', ' ')}.`}
            action={{
              label: 'Continue review →',
              onClick: () => navigate(`/sprints/${activeSprintQuery.data!.sprintId}/review`),
            }}
          />
        </div>
      ) : null}
      <section className="grid gap-6">
        <article className="space-y-4 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card backdrop-blur-xl">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-semibold text-slate-100">Create Team</h2>
            <button
              type="button"
              className="rounded-lg bg-blue-500/85 px-3 py-2 text-sm font-semibold text-white shadow-lg shadow-blue-900/30 hover:bg-blue-600/85 disabled:opacity-50"
              onClick={handleLoadSamples}
              disabled={isLoadingSamples}
              title="Load 2 sample teams (PayFlow & ShopHub) with developers and realistic epics"
            >
              {isLoadingSamples ? 'Loading...' : '⚡ Load Samples'}
            </button>
          </div>
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
