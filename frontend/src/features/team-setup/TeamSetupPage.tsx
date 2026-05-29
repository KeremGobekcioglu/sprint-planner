import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { LoadingSpinner } from '../../components/shared/LoadingSpinner';
import { NoticeBanner } from '../../components/shared/NoticeBanner';
import { PageLayout } from '../../components/shared/PageLayout';
import { usePlanningStore } from '../../store/planningStore';
import type { Team } from '../../types/team';
import {
  createDeveloper, createTeam, getTrelloBoards, getTeams, linkTrelloBoard, loadSampleData,
  getTrelloMembers, linkTrelloMember,
} from '../../api/teamApi';
import type { TrelloMember } from '../../api/teamApi';
import { getTrelloStatus } from '../../api/profileApi';
import { getActiveSprint } from '../../api/sprintApi';
import { SAMPLE_DEVELOPERS, SAMPLE_TEAMS } from '../../api/sampleData';
import { DeveloperForm } from './DeveloperForm';
import { useTeamSetup } from './useTeamSetup';
import { HelpTooltip } from '../../components/shared/HelpTooltip';

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
  const [trelloBoardInput, setTrelloBoardInput] = useState('');
  const [selectedBoardId, setSelectedBoardId] = useState('');
  const [trelloMembers, setTrelloMembers] = useState<TrelloMember[] | null>(null);
  const [memberMappings, setMemberMappings] = useState<Record<string, string>>({});
  const { teamsQuery, developersQuery, createTeamMutation, createDeveloperMutation, removeDeveloperMutation } =
    useTeamSetup(selectedTeamId);

  const trelloBoardsQuery = useQuery({
    queryKey: ['trello-boards'],
    queryFn: getTrelloBoards,
    enabled: false,
    retry: false,
  });

  const linkBoardMutation = useMutation({
    mutationFn: ({ teamId, boardId }: { teamId: string; boardId: string }) => linkTrelloBoard(teamId, boardId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['teams'] });
      setTrelloBoardInput('');
      setSelectedBoardId('');
    },
  });

  const fetchMembersMutation = useMutation({
    mutationFn: (teamId: string) => getTrelloMembers(teamId),
    onSuccess: (members) => {
      setTrelloMembers(members);
      setMemberMappings({});
    },
  });

  const saveMappingsMutation = useMutation({
    mutationFn: async ({ teamId, mappings }: { teamId: string; mappings: Record<string, string> }) => {
      await Promise.all(
        Object.entries(mappings)
          .filter(([, devId]) => devId)
          .map(([trelloMemberId, devId]) => linkTrelloMember(teamId, devId, trelloMemberId)),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['developers', selectedTeamId] });
    },
  });

  useEffect(() => {
    setTrelloMembers(null);
    setMemberMappings({});
  }, [selectedTeamId]);

  const activeSprintQuery = useQuery({
    queryKey: ['active-sprint'],
    queryFn: getActiveSprint,
    staleTime: 1000 * 60 * 2,
    retry: false,
  });

  const trelloStatusQuery = useQuery({
    queryKey: ['trello-status'],
    queryFn: getTrelloStatus,
    staleTime: 1000 * 60 * 5,
    retry: false,
  });

  const isTrelloConnected = trelloStatusQuery.data?.connected ?? false;

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
              <div className="rounded-xl border border-white/10 bg-slate-950/40 p-4 space-y-3">
                <div className="flex items-center gap-1.5">
                  <p className="text-sm font-semibold text-slate-200">Trello Board</p>
                  <HelpTooltip
                    text="Paste the board ID from your Trello board URL: trello.com/b/{BOARD_ID}/board-name"
                    position="right"
                  />
                </div>

                {isTrelloConnected && !selectedTeam.trelloBoardId && (
                  <div className="rounded-lg border border-amber-500/30 bg-amber-900/20 px-4 py-3 text-sm">
                    <p className="text-amber-200">
                      Your Trello account is connected but no board is linked to this team.
                      Link a board to enable sprint push.
                    </p>
                  </div>
                )}

                {!isTrelloConnected && (
                  <div className="rounded-lg bg-amber-500/10 border border-amber-500/30 px-4 py-3 text-sm">
                    <p className="text-amber-300">
                      Connect your Trello account on the{' '}
                      <Link to="/profile" className="underline hover:text-amber-200">
                        Profile page
                      </Link>{' '}
                      to enable Trello features.
                    </p>
                  </div>
                )}

                {isTrelloConnected && (
                  <>
                    {selectedTeam.trelloBoardId ? (
                      <p className="text-xs text-slate-400">
                        Currently linked: <span className="font-mono text-slate-300">{selectedTeam.trelloBoardId}</span>
                      </p>
                    ) : (
                      <p className="text-xs text-slate-500">No Trello board linked yet.</p>
                    )}
                    <div className="flex gap-2 flex-wrap">
                      <button
                        type="button"
                        className="rounded-lg border border-slate-500/40 px-3 py-1.5 text-xs font-medium text-slate-300 hover:bg-slate-800/40 disabled:opacity-50"
                        onClick={() => trelloBoardsQuery.refetch()}
                        disabled={trelloBoardsQuery.isFetching}
                      >
                        {trelloBoardsQuery.isFetching ? 'Fetching...' : 'Fetch my Trello boards'}
                      </button>
                    </div>
                  </>
                )}
                {isTrelloConnected && trelloBoardsQuery.isError ? (
                  <ErrorBanner message="Could not fetch Trello boards. Connect your Trello account first." />
                ) : null}
                {isTrelloConnected && trelloBoardsQuery.data && trelloBoardsQuery.data.length > 0 ? (
                  <div className="space-y-2">
                    <select
                      className="w-full rounded-lg border border-slate-500/40 bg-slate-900 px-3 py-2 text-sm text-slate-200"
                      value={selectedBoardId}
                      onChange={(e) => setSelectedBoardId(e.target.value)}
                    >
                      <option value="">— select a board —</option>
                      {trelloBoardsQuery.data.map((board) => (
                        <option key={board.id} value={board.id}>
                          {board.name}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      className="rounded-lg bg-blue-500/85 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                      disabled={!selectedBoardId || linkBoardMutation.isPending}
                      onClick={() => {
                        if (selectedBoardId) {
                          linkBoardMutation.mutate({ teamId: selectedTeam.id, boardId: selectedBoardId });
                        }
                      }}
                    >
                      {linkBoardMutation.isPending ? 'Linking...' : 'Link selected board'}
                    </button>
                  </div>
                ) : null}
                {isTrelloConnected && (
                  <div className="flex gap-2 items-center">
                    <input
                      className="flex-1 rounded-lg border border-slate-500/40 px-3 py-1.5 text-sm bg-slate-900 text-slate-200 placeholder:text-slate-500"
                      placeholder="Or paste board ID manually"
                      value={trelloBoardInput}
                      onChange={(e) => setTrelloBoardInput(e.target.value)}
                    />
                    <button
                      type="button"
                      className="rounded-lg bg-blue-500/85 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                      disabled={!trelloBoardInput.trim() || linkBoardMutation.isPending}
                      onClick={() => {
                        const boardId = trelloBoardInput.trim();
                        if (boardId) {
                          linkBoardMutation.mutate({ teamId: selectedTeam.id, boardId });
                        }
                      }}
                    >
                      Link
                    </button>
                  </div>
                )}
                {isTrelloConnected && linkBoardMutation.isError ? (
                  <ErrorBanner message="Failed to link Trello board." error={linkBoardMutation.error} />
                ) : null}
                {isTrelloConnected && linkBoardMutation.isSuccess ? (
                  <p className="text-xs text-emerald-400">Board linked successfully.</p>
                ) : null}
              </div>

              {/* Sync Trello Members — only when connected and board is linked */}
              {isTrelloConnected && selectedTeam.trelloBoardId ? (
                <div className="rounded-xl border border-white/10 bg-slate-950/40 p-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <p className="text-sm font-semibold text-slate-200">Sync Trello Members</p>
                    <button
                      type="button"
                      className="rounded-lg border border-slate-500/40 px-3 py-1.5 text-xs font-medium text-slate-300 hover:bg-slate-800/40 disabled:opacity-50"
                      onClick={() => fetchMembersMutation.mutate(selectedTeam.id)}
                      disabled={fetchMembersMutation.isPending}
                    >
                      {fetchMembersMutation.isPending ? 'Fetching...' : 'Fetch Trello Members'}
                    </button>
                  </div>

                  {fetchMembersMutation.isError ? (
                    <ErrorBanner message="Could not fetch Trello board members. Check credentials." error={fetchMembersMutation.error} />
                  ) : null}

                  {trelloMembers !== null ? (
                    trelloMembers.length === 0 ? (
                      <p className="text-xs text-slate-500">No members found on this board.</p>
                    ) : (
                      <div className="space-y-3">
                        <p className="text-xs text-slate-400">Map each Trello member to a developer on this team:</p>
                        <div className="space-y-2">
                          {trelloMembers.map((member) => (
                            <div key={member.id} className="flex items-center gap-3">
                              <div className="flex-1 min-w-0">
                                <p className="text-sm text-slate-200 truncate">{member.fullName}</p>
                                <p className="text-xs text-slate-500">@{member.username}</p>
                              </div>
                              <select
                                className="rounded-lg border border-slate-500/40 bg-slate-900 px-2 py-1.5 text-sm text-slate-200"
                                value={memberMappings[member.id] ?? ''}
                                onChange={(e) =>
                                  setMemberMappings((prev) => ({ ...prev, [member.id]: e.target.value }))
                                }
                              >
                                <option value="">— select developer —</option>
                                {(developersQuery.data ?? []).map((dev) => (
                                  <option key={dev.id} value={dev.id}>
                                    {dev.name}
                                  </option>
                                ))}
                              </select>
                            </div>
                          ))}
                        </div>
                        <button
                          type="button"
                          className="rounded-lg bg-emerald-500/85 px-3 py-1.5 text-xs font-semibold text-slate-950 disabled:opacity-50"
                          disabled={saveMappingsMutation.isPending || Object.values(memberMappings).every((v) => !v)}
                          onClick={() =>
                            saveMappingsMutation.mutate({ teamId: selectedTeam.id, mappings: memberMappings })
                          }
                        >
                          {saveMappingsMutation.isPending ? 'Saving...' : 'Save Mappings'}
                        </button>
                        {saveMappingsMutation.isSuccess ? (
                          <p className="text-xs text-emerald-400">Mappings saved.</p>
                        ) : null}
                        {saveMappingsMutation.isError ? (
                          <ErrorBanner message="Failed to save member mappings." error={saveMappingsMutation.error} />
                        ) : null}
                      </div>
                    )
                  ) : null}
                </div>
              ) : null}

              <DeveloperForm
                isSubmitting={createDeveloperMutation.isPending}
                onSubmit={(values) => {
                  createDeveloperMutation.mutate({ teamId: selectedTeam.id, payload: values });
                }}
              />

              {developersQuery.isLoading ? <LoadingSpinner label="Loading developers..." /> : null}
              {developersQuery.isError ? <ErrorBanner message="Unable to load developers for this team." /> : null}

              {developersQuery.data?.length === 0 && (
                <div className="rounded-xl border border-dashed border-white/10 bg-slate-950/30 px-5 py-8 text-center">
                  <p className="text-sm text-slate-300">No developers yet. Add your first developer above.</p>
                  <p className="mt-1 text-xs text-slate-500">
                    The AI uses developer capabilities to assign tasks intelligently.
                  </p>
                </div>
              )}

              <div className="space-y-2">
                {developersQuery.data?.map((developer) => (
                  <div
                    key={developer.id}
                    className="flex items-center justify-between rounded-lg border border-white/10 bg-slate-950/45 p-3"
                  >
                    <div className="flex items-center gap-2">
                      {developer.trelloMemberId ? (
                        <span className="text-emerald-400 text-sm" title="Trello member linked">✓</span>
                      ) : null}
                      <div>
                        <p className="font-medium text-slate-100">{developer.name}</p>
                        <p className="text-xs uppercase tracking-wide text-slate-300">
                          {developer.role} • {developer.capabilities?.join(', ') || 'no capabilities'} • Velocity {developer.baseVelocity}
                        </p>
                      </div>
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
