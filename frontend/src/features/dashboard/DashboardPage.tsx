import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getDashboardData } from '../../api/sprintApi';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { LoadingSpinner } from '../../components/shared/LoadingSpinner';
import { PageLayout } from '../../components/shared/PageLayout';
import type { ActiveSprintSummary, RecentSprintSummary } from '../../types/dashboard';
import type { TeamSummary } from '../../api/profileApi';

const STATUS_BADGE: Record<string, string> = {
  PLANNING: 'bg-blue-500/20 text-blue-300 border-blue-500/30',
  IN_REVIEW: 'bg-amber-500/20 text-amber-300 border-amber-500/30',
  ACTIVE: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30',
  COMPLETED: 'bg-slate-500/20 text-slate-300 border-slate-500/30',
};

const GOAL_RESULT_BADGE: Record<string, string> = {
  YES: 'bg-emerald-500/20 text-emerald-300',
  PARTIALLY: 'bg-amber-500/20 text-amber-300',
  NO: 'bg-rose-500/20 text-rose-300',
};

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

function ActiveSprintCard({ sprint }: { sprint: ActiveSprintSummary }) {
  const navigate = useNavigate();
  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-white/10 bg-slate-900/60 p-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="font-semibold text-slate-100">{sprint.teamName}</p>
          <span className="inline-block rounded-full bg-slate-700/60 px-2 py-0.5 text-xs text-slate-400">
            {sprint.teamDomain}
          </span>
        </div>
        <span className={`rounded-full border px-2.5 py-0.5 text-xs font-semibold ${STATUS_BADGE[sprint.status] ?? STATUS_BADGE['PLANNING']}`}>
          {sprint.status.replace('_', ' ')}
        </span>
      </div>

      <p className="text-sm text-slate-200">{sprint.sprintGoal}</p>

      <div className="flex gap-4 text-xs text-slate-400">
        <span>{sprint.storyCount} stories</span>
        <span>{sprint.totalPoints} pts</span>
        <span>Started {formatDate(sprint.plannedAt)}</span>
      </div>

      <div className="flex gap-2">
        <button
          className="flex-1 rounded-lg bg-blue-500/85 px-3 py-1.5 text-xs font-semibold text-white hover:bg-blue-600/85"
          onClick={() => navigate(`/sprints/${sprint.sprintId}/review`)}
        >
          Continue to Review
        </button>
        <button
          className="flex-1 rounded-lg border border-slate-500/40 px-3 py-1.5 text-xs font-medium text-slate-300 hover:bg-slate-800/40"
          onClick={() => navigate(`/sprints/${sprint.sprintId}/close`)}
        >
          Close Sprint
        </button>
      </div>
    </div>
  );
}

function TeamCard({ team }: { team: TeamSummary }) {
  const navigate = useNavigate();
  return (
    <button
      className="flex flex-col gap-2 rounded-2xl border border-white/10 bg-slate-900/60 p-4 text-left hover:border-slate-300/30 hover:bg-slate-900/80 transition"
      onClick={() => navigate(`/teams/${team.teamId}/plan`)}
    >
      <p className="font-semibold text-slate-100">{team.name}</p>
      <span className="inline-block rounded-full bg-slate-700/60 px-2 py-0.5 text-xs text-slate-400">
        {team.domain}
      </span>
      <div className="flex gap-3 text-xs text-slate-400">
        <span>{team.developerCount} devs</span>
        <span>{team.completedSprints} sprints</span>
      </div>
      {team.lastSprintAt ? (
        <p className="text-xs text-slate-500">Last: {formatDate(team.lastSprintAt)}</p>
      ) : null}
    </button>
  );
}

function RecentSprintRow({ sprint }: { sprint: RecentSprintSummary }) {
  const navigate = useNavigate();
  const completionRate =
    sprint.plannedPts > 0 ? Math.round((sprint.completedPts / sprint.plannedPts) * 100) : 0;

  return (
    <button
      className="flex w-full items-center gap-3 rounded-xl border border-white/5 bg-slate-950/40 p-3 text-left hover:bg-slate-900/50 transition"
      onClick={() => navigate(`/sprints/${sprint.sprintId}/close`)}
    >
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-slate-100">{sprint.teamName}</p>
        <p className="truncate text-xs text-slate-400">{sprint.sprintGoal}</p>
      </div>

      {sprint.goalResult ? (
        <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold ${GOAL_RESULT_BADGE[sprint.goalResult]}`}>
          {sprint.goalResult}
        </span>
      ) : null}

      <div className="shrink-0 text-right">
        <p className="text-sm font-semibold text-slate-100">{completionRate}%</p>
        <p className="text-xs text-slate-500">{formatDate(sprint.submittedAt)}</p>
      </div>
    </button>
  );
}

export function DashboardPage() {
  const navigate = useNavigate();
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['dashboard'],
    queryFn: getDashboardData,
    staleTime: 1000 * 60 * 2,
  });

  return (
    <PageLayout title="Dashboard" subtitle="Overview of your teams and sprints.">
      {isLoading ? <LoadingSpinner label="Loading dashboard..." /> : null}
      {isError ? <ErrorBanner message="Could not load dashboard." error={error} /> : null}

      {data ? (
        <div className="grid gap-6">
          {/* Section 1 — Active Sprints */}
          <section className="space-y-3 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card">
            <h2 className="text-lg font-semibold text-slate-100">Active Sprints</h2>
            {data.activeSprints.length === 0 ? (
              <p className="text-sm text-slate-400">
                No active sprints.{' '}
                <button className="text-sky-300 underline" onClick={() => navigate('/teams')}>
                  Go to a team to start planning.
                </button>
              </p>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {data.activeSprints.map((sprint) => (
                  <ActiveSprintCard key={sprint.sprintId} sprint={sprint} />
                ))}
              </div>
            )}
          </section>

          {/* Section 2 — My Teams */}
          <section className="space-y-3 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card">
            <h2 className="text-lg font-semibold text-slate-100">My Teams</h2>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {data.teams.map((team) => (
                <TeamCard key={team.teamId} team={team} />
              ))}
              <button
                className="flex items-center justify-center rounded-2xl border border-dashed border-white/20 bg-slate-900/30 p-4 text-2xl text-slate-500 hover:border-slate-400/40 hover:text-slate-300 transition"
                onClick={() => navigate('/teams')}
                title="Create a new team"
              >
                +
              </button>
            </div>
          </section>

          {/* Section 3 — Recent Activity */}
          <section className="space-y-3 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card">
            <h2 className="text-lg font-semibold text-slate-100">Recent Activity</h2>
            {data.recentSprints.length === 0 ? (
              <p className="text-sm text-slate-400">No completed sprints yet.</p>
            ) : (
              <div className="space-y-2">
                {data.recentSprints.map((sprint) => (
                  <RecentSprintRow key={sprint.sprintId} sprint={sprint} />
                ))}
              </div>
            )}
          </section>
        </div>
      ) : null}
    </PageLayout>
  );
}
