import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { LoadingSpinner } from '../../components/shared/LoadingSpinner';
import { PageLayout } from '../../components/shared/PageLayout';
import { useProfile } from '../../hooks/useProfile';
import { getTrelloStatus, disconnectTrello } from '../../api/profileApi';
import type { TeamSummary } from '../../api/profileApi';
import { TeamMemoryPanel } from './TeamMemoryPanel';

function MemoryPanel({ team }: { team: TeamSummary }) {
  const [open, setOpen] = useState(false);

  return (
    <div className="overflow-hidden rounded-2xl border border-white/10 bg-slate-950/40">
      <button
        type="button"
        className="flex w-full items-center gap-3 px-4 py-3.5 text-left transition-colors hover:bg-slate-800/20"
        onClick={() => setOpen((o) => !o)}
      >
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-teal-500/10 text-base">
          🧠
        </div>
        <div className="min-w-0 flex-1">
          <p className="font-semibold text-slate-100">{team.name}</p>
          <p className="text-xs text-slate-500">
            {team.completedSprints} sprint{team.completedSprints !== 1 ? 's' : ''} completed
          </p>
        </div>
        <span className={`text-slate-400 transition-transform duration-200 ${open ? 'rotate-180' : ''}`}>▼</span>
      </button>

      {open && (
        <div className="border-t border-white/10 px-4 pb-5 pt-4">
          <TeamMemoryPanel teamId={team.teamId} />
        </div>
      )}
    </div>
  );
}

export function ProfilePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { profileQuery, statsQuery, updateNameMutation } = useProfile();
  const [editingName, setEditingName] = useState(false);
  const [nameInput, setNameInput] = useState('');
  const [trelloConnectSuccess, setTrelloConnectSuccess] = useState(false);

  // Check for success state from TrelloCallbackPage
  useEffect(() => {
    if (location.state?.trelloConnected) {
      setTrelloConnectSuccess(true);
      // Clear the state so it doesn't persist on refresh
      window.history.replaceState({}, document.title);
      // Auto-hide after 5 seconds
      setTimeout(() => setTrelloConnectSuccess(false), 5000);
    }
  }, [location.state]);

  const trelloStatusQuery = useQuery({
    queryKey: ['trello-status'],
    queryFn: getTrelloStatus,
    staleTime: 1000 * 60 * 5,
    retry: false,
  });

  const disconnectTrelloMutation = useMutation({
    mutationFn: disconnectTrello,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['trello-status'] });
    },
  });

  function handleConnectTrello() {
    const TRELLO_API_KEY = import.meta.env.VITE_TRELLO_API_KEY;
    if (!TRELLO_API_KEY) {
      console.error('VITE_TRELLO_API_KEY is not set');
      return;
    }
    const returnUrl = `${window.location.origin}/trello/callback`;
    const trelloAuthUrl = `https://trello.com/1/authorize?key=${TRELLO_API_KEY}&name=Sprint+Planner&expiration=never&scope=read,write&response_type=token&callback_method=fragment&return_url=${encodeURIComponent(returnUrl)}`;
    window.open(trelloAuthUrl, '_blank');
  }

  function startEditName() {
    setNameInput(profileQuery.data?.name ?? '');
    setEditingName(true);
  }

  function saveName() {
    if (nameInput.trim()) {
      updateNameMutation.mutate(nameInput.trim(), {
        onSuccess: () => setEditingName(false),
      });
    }
  }

  return (
    <PageLayout title="Profile" subtitle="Your identity, teams, and what the system has learned.">
      <div className="mb-4">
        <button
          type="button"
          onClick={() => navigate('/dashboard')}
          className="rounded-lg border border-slate-500/40 px-3 py-1.5 text-sm font-medium text-slate-300 hover:bg-slate-800/40"
        >
          ← Back to Dashboard
        </button>
      </div>

      <div className="grid gap-6">
        {/* Section 1 — Identity */}
        <section className="space-y-3 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card">
          <h2 className="text-lg font-semibold text-slate-100">Identity</h2>

          {profileQuery.isLoading ? <LoadingSpinner label="Loading profile..." /> : null}
          {profileQuery.isError ? <ErrorBanner message="Could not load profile." /> : null}

          {profileQuery.data ? (
            <div className="space-y-2">
              <div className="flex items-center gap-3">
                {editingName ? (
                  <>
                    <input
                      className="flex-1 rounded-lg border border-slate-500/40 px-3 py-1.5 text-sm text-slate-100"
                      value={nameInput}
                      onChange={(e) => setNameInput(e.target.value)}
                      onKeyDown={(e) => { if (e.key === 'Enter') saveName(); }}
                      autoFocus
                    />
                    <button
                      onClick={saveName}
                      disabled={updateNameMutation.isPending}
                      className="rounded px-3 py-1.5 text-sm font-semibold text-emerald-300 hover:bg-emerald-900/30 disabled:opacity-50"
                    >
                      Save
                    </button>
                    <button
                      onClick={() => setEditingName(false)}
                      className="rounded px-3 py-1.5 text-sm text-slate-400 hover:bg-slate-800/40"
                    >
                      Cancel
                    </button>
                  </>
                ) : (
                  <>
                    <span className="text-xl font-bold text-slate-100">{profileQuery.data.name}</span>
                    <button
                      onClick={startEditName}
                      className="rounded px-2 py-0.5 text-xs text-slate-400 hover:bg-slate-800/40"
                    >
                      Edit
                    </button>
                  </>
                )}
              </div>
              <p className="text-sm text-slate-400">{profileQuery.data.email}</p>
              <span className="inline-block rounded-full bg-sky-500/20 px-2 py-0.5 text-xs font-semibold text-sky-200">
                {profileQuery.data.role}
              </span>
            </div>
          ) : null}
        </section>

        {/* Section — Trello Connection */}
        <section id="trello" className="space-y-3 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card scroll-mt-20">
          <h2 className="text-lg font-semibold text-slate-100">Trello Connection</h2>

          {trelloConnectSuccess && (
            <div className="rounded-lg bg-emerald-500/20 px-4 py-2 text-sm text-emerald-300">
              Trello connected successfully!
            </div>
          )}

          {trelloStatusQuery.isLoading ? <LoadingSpinner label="Checking Trello status..." /> : null}
          {trelloStatusQuery.isError ? <ErrorBanner message="Could not check Trello connection status." /> : null}

          {trelloStatusQuery.data ? (
            trelloStatusQuery.data.connected ? (
              <div className="space-y-3">
                <div className="flex items-center gap-2">
                  <span className="text-emerald-400 text-lg">✓</span>
                  <span className="text-slate-200">Trello connected</span>
                </div>
                <button
                  onClick={() => disconnectTrelloMutation.mutate()}
                  disabled={disconnectTrelloMutation.isPending}
                  className="rounded-lg border border-rose-500/40 bg-rose-900/20 px-4 py-2 text-sm font-medium text-rose-300 hover:bg-rose-900/30 disabled:opacity-50"
                >
                  {disconnectTrelloMutation.isPending ? 'Disconnecting...' : 'Disconnect Trello'}
                </button>
              </div>
            ) : (
              <div className="space-y-3">
                <p className="text-sm text-slate-400">
                  Connect your Trello account to push sprint plans directly to your boards.
                </p>
                {!import.meta.env.VITE_TRELLO_API_KEY ? (
                  <p className="text-sm text-amber-400">
                    Trello API key not configured. Set VITE_TRELLO_API_KEY in your environment.
                  </p>
                ) : (
                  <button
                    onClick={handleConnectTrello}
                    className="rounded-lg bg-sky-500/85 px-4 py-2 text-sm font-semibold text-white shadow-lg shadow-sky-900/30 hover:bg-sky-600/85"
                  >
                    Connect Trello
                  </button>
                )}
              </div>
            )
          ) : null}

          {disconnectTrelloMutation.isError && (
            <ErrorBanner message="Failed to disconnect Trello." error={disconnectTrelloMutation.error} />
          )}
        </section>

        {/* Section 2 — My Teams */}
        <section className="space-y-3 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card">
          <h2 className="text-lg font-semibold text-slate-100">My Teams</h2>

          {profileQuery.data?.teams.length === 0 ? (
            <p className="text-sm text-slate-400">
              You haven&apos;t created any teams yet.{' '}
              <button onClick={() => navigate('/teams')} className="text-sky-300 underline">
                Create your first team →
              </button>
            </p>
          ) : null}

          {profileQuery.data && profileQuery.data.teams.length > 0 ? (
            <>
              {/* Mobile: card list */}
              <div className="space-y-3 sm:hidden">
                {profileQuery.data.teams.map((team) => (
                  <button
                    key={team.teamId}
                    className="w-full rounded-lg border border-white/10 bg-slate-950/40 p-3 text-left hover:bg-slate-900/50"
                    onClick={() => navigate(`/teams/${team.teamId}/plan`)}
                  >
                    <p className="font-medium text-sky-300">{team.name}</p>
                    <p className="text-xs text-slate-400">{team.domain} · {team.developerCount} devs · {team.completedSprints} sprints</p>
                    {team.lastSprintAt ? (
                      <p className="text-xs text-slate-500">Last: {new Date(team.lastSprintAt).toLocaleDateString()}</p>
                    ) : null}
                  </button>
                ))}
              </div>
              {/* Desktop: table */}
              <div className="hidden overflow-x-auto sm:block">
                <table className="w-full text-sm text-slate-200">
                  <thead>
                    <tr className="border-b border-white/10 text-left text-xs uppercase tracking-wide text-slate-400">
                      <th className="pb-2 pr-4">Team</th>
                      <th className="pb-2 pr-4">Domain</th>
                      <th className="pb-2 pr-4">Developers</th>
                      <th className="pb-2 pr-4">Sprints Completed</th>
                      <th className="pb-2">Last Sprint</th>
                    </tr>
                  </thead>
                  <tbody>
                    {profileQuery.data.teams.map((team) => (
                      <tr
                        key={team.teamId}
                        className="cursor-pointer border-b border-white/5 hover:bg-slate-800/30"
                        onClick={() => navigate(`/teams/${team.teamId}/plan`)}
                      >
                        <td className="py-2 pr-4 font-medium text-sky-300">{team.name}</td>
                        <td className="py-2 pr-4 text-slate-400">{team.domain}</td>
                        <td className="py-2 pr-4">{team.developerCount}</td>
                        <td className="py-2 pr-4">{team.completedSprints}</td>
                        <td className="py-2 text-slate-400">
                          {team.lastSprintAt ? new Date(team.lastSprintAt).toLocaleDateString() : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          ) : null}
        </section>

        {/* Section 3 — Stats */}
        <section className="space-y-3 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card">
          <h2 className="text-lg font-semibold text-slate-100">Stats</h2>

          {statsQuery.isLoading ? <LoadingSpinner label="Loading stats..." /> : null}
          {statsQuery.isError ? <ErrorBanner message="Could not load stats." /> : null}

          {statsQuery.data ? (
            statsQuery.data.totalSprintsCompleted === 0 ? (
              <div className="rounded-xl border border-dashed border-white/10 bg-slate-950/30 px-5 py-8 text-center">
                <p className="text-sm text-slate-300">No sprints completed yet.</p>
                <p className="mt-1 text-xs text-slate-500">
                  Complete your first sprint review to see stats here.
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <div className="rounded-xl border border-white/10 bg-slate-950/40 p-4 text-center">
                  <p className="text-2xl font-bold text-slate-100">{statsQuery.data.totalSprintsPlanned}</p>
                  <p className="mt-1 text-xs text-slate-400">Sprints planned</p>
                </div>
                <div className="rounded-xl border border-white/10 bg-slate-950/40 p-4 text-center">
                  <p className="text-2xl font-bold text-slate-100">{statsQuery.data.totalSprintsCompleted}</p>
                  <p className="mt-1 text-xs text-slate-400">Sprints completed</p>
                </div>
                <div className="rounded-xl border border-white/10 bg-slate-950/40 p-4 text-center">
                  <p className="text-2xl font-bold text-slate-100">
                    {Math.round(statsQuery.data.averageVelocityAccuracy * 100)}%
                  </p>
                  <p className="mt-1 text-xs text-slate-400">Avg velocity accuracy</p>
                </div>
                <div className="rounded-xl border border-white/10 bg-slate-950/40 p-4 text-center">
                  <p className="text-2xl font-bold text-slate-100">{statsQuery.data.goalHitRate}%</p>
                  <p className="mt-1 text-xs text-slate-400">Goal hit rate</p>
                </div>
              </div>
            )
          ) : null}
        </section>

        {/* Section 4 — Memory Viewer */}
        <section id="memory" className="scroll-mt-20 space-y-3 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold text-slate-100">AI Memory</h2>
            <span className="rounded-full border border-teal-500/30 bg-teal-500/10 px-2 py-0.5 text-xs font-medium text-teal-300">
              per team
            </span>
          </div>
          <p className="text-xs text-slate-400">
            What the AI has learned from your past sprints — team preferences and sprint history.
            Expand a team to inspect or reset.
          </p>

          {profileQuery.data?.teams.map((team) => (
            <MemoryPanel key={team.teamId} team={team} />
          ))}

          {profileQuery.data?.teams.length === 0 ? (
            <p className="text-sm text-slate-400">No teams yet — complete a sprint to see memory here.</p>
          ) : null}
        </section>
      </div>
    </PageLayout>
  );
}
