import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createTeam, createDeveloper } from '../../api/teamApi';
import { getTrelloStatus } from '../../api/profileApi';
import { useAuthStore } from '../../store/authStore';
import { DeveloperForm } from '../team-setup/DeveloperForm';
import type { TeamDomain } from '../../types/team';

const STEPS = [
  { label: 'Welcome' },
  { label: 'Your Team' },
  { label: 'Trello' },
  { label: 'Ready' },
] as const;

interface TeamFormValues {
  name: string;
  domain: TeamDomain;
  sprintLengthDays: number;
}

interface PendingDev {
  name: string;
  role: string;
  capabilities: string[];
  baseVelocity: number;
}

function ProgressIndicator({ step }: { step: number }) {
  return (
    <div className="mb-10 flex items-start justify-center">
      {STEPS.map((s, i) => (
        <div key={i} className="flex items-start">
          <div className="flex flex-col items-center gap-1">
            <div
              className={`flex h-8 w-8 items-center justify-center rounded-full border-2 text-xs font-bold transition-all ${
                i < step
                  ? 'border-emerald-400 bg-emerald-400 text-slate-900'
                  : i === step
                  ? 'border-blue-400 bg-blue-500 text-white'
                  : 'border-slate-600 bg-slate-900 text-slate-500'
              }`}
            >
              {i < step ? '✓' : i + 1}
            </div>
            <span
              className={`text-xs font-medium ${
                i === step ? 'text-slate-200' : i < step ? 'text-emerald-400' : 'text-slate-600'
              }`}
            >
              {s.label}
            </span>
          </div>
          {i < STEPS.length - 1 && (
            <div
              className={`mx-1 mt-4 h-0.5 w-10 sm:w-16 transition-colors ${
                i < step ? 'bg-emerald-400' : 'bg-slate-700'
              }`}
            />
          )}
        </div>
      ))}
    </div>
  );
}

function StepWelcome({ onNext }: { onNext: () => void }) {
  return (
    <div className="space-y-8">
      <div className="text-center">
        <h1 className="text-3xl font-bold text-slate-100">Welcome to Sprint Planner</h1>
        <p className="mt-2 text-slate-400">AI-powered sprint planning for software teams</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-2xl border border-white/10 bg-slate-900/60 p-5">
          <div className="mb-3 text-2xl">🤖</div>
          <h3 className="mb-1 font-semibold text-slate-100">AI Planning</h3>
          <p className="text-sm text-slate-400">
            Paste your epics. Get a full sprint plan in under 90 seconds.
          </p>
        </div>
        <div className="rounded-2xl border border-white/10 bg-slate-900/60 p-5">
          <div className="mb-3 text-2xl">📋</div>
          <h3 className="mb-1 font-semibold text-slate-100">Review &amp; Edit</h3>
          <p className="text-sm text-slate-400">
            See the full sprint plan. Reassign tasks, adjust points, defer work.
          </p>
        </div>
        <div className="rounded-2xl border border-white/10 bg-slate-900/60 p-5">
          <div className="mb-3 text-2xl">🔗</div>
          <h3 className="mb-1 font-semibold text-slate-100">Trello Sync</h3>
          <p className="text-sm text-slate-400">
            Push your approved plan directly to your Trello board.
          </p>
        </div>
      </div>

      <div className="flex justify-center">
        <button
          onClick={onNext}
          className="rounded-xl bg-blue-500 px-8 py-3 font-semibold text-white hover:bg-blue-600 transition-colors"
        >
          Get Started →
        </button>
      </div>
    </div>
  );
}

function StepTeam({
  onNext,
  onSkip,
  onTeamCreated,
}: {
  onNext: () => void;
  onSkip: () => void;
  onTeamCreated: (teamId: string) => void;
}) {
  const queryClient = useQueryClient();
  const [pendingDevs, setPendingDevs] = useState<PendingDev[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<TeamFormValues>({
    defaultValues: { name: '', domain: 'GENERIC', sprintLengthDays: 14 },
  });

  function removeDev(index: number) {
    setPendingDevs((prev) => prev.filter((_, i) => i !== index));
  }

  const handleSubmit = form.handleSubmit(async (values) => {
    setIsSubmitting(true);
    setError(null);
    try {
      const team = await createTeam(values);
      await Promise.all(
        pendingDevs.map((dev) =>
          createDeveloper(team.id, {
            name: dev.name,
            role: dev.role,
            capabilities: dev.capabilities,
            baseVelocity: dev.baseVelocity,
          }),
        ),
      );
      queryClient.invalidateQueries({ queryKey: ['teams'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      onTeamCreated(team.id);
      onNext();
    } catch {
      setError('Failed to create team. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  });

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-100">Set up your team</h2>
        <p className="mt-1 text-slate-400">
          Add your developers and their roles. The AI uses this to assign tasks intelligently.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="rounded-2xl border border-white/10 bg-slate-900/60 p-5 space-y-4">
          <h3 className="font-semibold text-slate-200">Team details</h3>
          <div className="grid gap-3 sm:grid-cols-2">
            <input
              className="rounded-lg border border-slate-500/40 bg-slate-950/60 px-3 py-2 text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-400"
              placeholder="Team name"
              {...form.register('name', { required: true })}
            />
            <select
              className="rounded-lg border border-slate-500/40 bg-slate-950/60 px-3 py-2 text-slate-100 focus:outline-none focus:border-blue-400"
              {...form.register('domain')}
            >
              <option value="E_COMMERCE">E-Commerce</option>
              <option value="FINTECH">Fintech</option>
              <option value="GENERIC">Generic</option>
            </select>
            <div className="sm:col-span-2">
              <label className="mb-1 block text-xs text-slate-400">Sprint length (days)</label>
              <input
                type="number"
                min={7}
                className="w-full rounded-lg border border-slate-500/40 bg-slate-950/60 px-3 py-2 text-slate-100 focus:outline-none focus:border-blue-400"
                {...form.register('sprintLengthDays', { valueAsNumber: true, min: 7 })}
              />
            </div>
          </div>
        </div>

        <div className="rounded-2xl border border-white/10 bg-slate-900/60 p-5 space-y-3">
          <h3 className="font-semibold text-slate-200">Add developers</h3>
          <DeveloperForm
            isSubmitting={false}
            onSubmit={(values) => {
              setPendingDevs((prev) => [...prev, {
                name: values.name,
                role: values.role,
                capabilities: values.capabilities,
                baseVelocity: values.baseVelocity,
              }]);
            }}
          />
          {pendingDevs.length > 0 ? (
            <ul className="space-y-2">
              {pendingDevs.map((dev, i) => (
                <li key={i} className="flex items-center justify-between rounded-lg border border-white/5 bg-slate-950/40 px-3 py-2 text-sm">
                  <span className="text-slate-200">{dev.name}</span>
                  <span className="text-slate-400">{dev.role} · {dev.capabilities.join(', ')} · {dev.baseVelocity} pts</span>
                  <button type="button" onClick={() => removeDev(i)} className="text-slate-500 hover:text-rose-400 ml-3">✕</button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-xs text-slate-500">No developers added yet. You can add them now or later from the Teams page.</p>
          )}
        </div>

        {error && <p className="text-sm text-rose-400">{error}</p>}

        <div className="flex items-center justify-between pt-2">
          <button
            type="button"
            onClick={onSkip}
            className="text-sm text-slate-500 hover:text-slate-300 underline underline-offset-2"
          >
            I'll do this later
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-xl bg-blue-500 px-6 py-2.5 font-semibold text-white hover:bg-blue-600 disabled:opacity-60 transition-colors"
          >
            {isSubmitting ? 'Creating...' : 'Create Team →'}
          </button>
        </div>
      </form>
    </div>
  );
}

function StepTrello({
  isTrelloConnected,
  createdTeamId,
  onConnect,
  onNext,
  onRefreshStatus,
}: {
  isTrelloConnected: boolean;
  createdTeamId: string | null;
  onConnect: () => void;
  onNext: () => void;
  onRefreshStatus: () => void;
}) {
  const [boardId, setBoardId] = useState('');
  const [boardLinked, setBoardLinked] = useState(false);

  async function linkBoard() {
    if (!boardId.trim() || !createdTeamId) return;
    const { linkTrelloBoard } = await import('../../api/teamApi');
    await linkTrelloBoard(createdTeamId, boardId.trim());
    setBoardLinked(true);
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-100">Connect your Trello board (optional)</h2>
        <p className="mt-1 text-slate-400">
          Sprint Planner can push your approved plan directly to Trello as cards. You can skip this
          and connect later from your Profile.
        </p>
      </div>

      <div className="rounded-2xl border border-white/10 bg-slate-900/60 p-5 space-y-4">
        {isTrelloConnected ? (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="text-emerald-400">✓</span>
              <span className="text-slate-200">Trello connected</span>
            </div>
            {createdTeamId && (
              <div className="space-y-2">
                <p className="text-sm text-slate-400">Optionally link a board to your team:</p>
                <div className="flex gap-2">
                  <input
                    className="flex-1 rounded-lg border border-slate-500/40 bg-slate-950/60 px-3 py-2 text-sm text-slate-100 placeholder-slate-500"
                    placeholder="Trello board ID"
                    value={boardId}
                    onChange={(e) => setBoardId(e.target.value)}
                  />
                  <button
                    type="button"
                    onClick={linkBoard}
                    disabled={!boardId.trim() || boardLinked}
                    className="rounded-lg bg-blue-500/85 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
                  >
                    {boardLinked ? 'Linked ✓' : 'Link Board'}
                  </button>
                </div>
                <p className="text-xs text-slate-500">Find your board ID in the Trello URL: trello.com/b/&lt;BOARD_ID&gt;/...</p>
              </div>
            )}
          </div>
        ) : (
          <div className="space-y-3">
            <p className="text-sm text-slate-400">
              Connect your Trello account to enable the push feature.
            </p>
            {!import.meta.env.VITE_TRELLO_API_KEY ? (
              <p className="text-sm text-amber-400">Trello API key not configured.</p>
            ) : (
              <button
                type="button"
                onClick={onConnect}
                className="rounded-lg bg-sky-500/85 px-4 py-2 text-sm font-semibold text-white hover:bg-sky-600/85"
              >
                Connect Trello
              </button>
            )}
            <div>
              <button
                type="button"
                onClick={onRefreshStatus}
                className="text-xs text-slate-500 hover:text-slate-300 underline underline-offset-2"
              >
                Already connected? Check status
              </button>
            </div>
          </div>
        )}
      </div>

      <div className="flex items-center justify-between pt-2">
        <div />
        <button
          onClick={onNext}
          className="rounded-xl bg-blue-500 px-6 py-2.5 font-semibold text-white hover:bg-blue-600 transition-colors"
        >
          {isTrelloConnected ? 'Continue →' : 'Skip for now →'}
        </button>
      </div>
    </div>
  );
}

function StepReady({ createdTeamId, onFinish }: { createdTeamId: string | null; onFinish: () => void }) {
  const steps = [
    'Paste your epics on the Planning page',
    'The AI breaks them into tasks, estimates points, assigns to your team',
    'Review the plan — drag tasks, adjust points, defer work',
    'Approve and push to Trello',
    'When the sprint ends, close it and the AI learns from the outcome',
  ];

  return (
    <div className="space-y-6">
      <div className="text-center">
        <div className="mb-3 text-4xl">🎉</div>
        <h2 className="text-2xl font-bold text-slate-100">You're all set!</h2>
        <p className="mt-1 text-slate-400">Here's how a sprint works:</p>
      </div>

      <div className="rounded-2xl border border-white/10 bg-slate-900/60 p-5">
        <ol className="space-y-3">
          {steps.map((s, i) => (
            <li key={i} className="flex items-start gap-3">
              <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-500/20 text-xs font-bold text-blue-300">
                {i + 1}
              </span>
              <span className="text-sm text-slate-300">{s}</span>
            </li>
          ))}
        </ol>
      </div>

      <div className="flex justify-center pt-2">
        <button
          onClick={onFinish}
          className="rounded-xl bg-emerald-500 px-8 py-3 font-semibold text-slate-950 hover:bg-emerald-400 transition-colors"
        >
          Start Planning →
        </button>
      </div>
    </div>
  );
}

export function OnboardingPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { userId } = useAuthStore();
  const [step, setStep] = useState(0);
  const [createdTeamId, setCreatedTeamId] = useState<string | null>(null);

  const trelloStatusQuery = useQuery({
    queryKey: ['trello-status'],
    queryFn: getTrelloStatus,
    staleTime: 1000 * 60 * 5,
    retry: false,
  });

  const isTrelloConnected = trelloStatusQuery.data?.connected ?? false;

  function skipOnboarding() {
    localStorage.setItem(`onboarding_complete_${userId}`, 'true');
    navigate('/dashboard', { replace: true });
  }

  function handleConnectTrello() {
    const TRELLO_API_KEY = import.meta.env.VITE_TRELLO_API_KEY;
    if (!TRELLO_API_KEY) return;
    const returnUrl = `${window.location.origin}/trello/callback`;
    const url = `https://trello.com/1/authorize?key=${TRELLO_API_KEY}&name=Sprint+Planner&expiration=never&scope=read,write&response_type=token&callback_method=fragment&return_url=${encodeURIComponent(returnUrl)}`;
    window.open(url, '_blank');
  }

  function finishOnboarding() {
    localStorage.setItem(`onboarding_complete_${userId}`, 'true');
    if (createdTeamId) {
      navigate(`/teams/${createdTeamId}/plan`, { replace: true });
    } else {
      navigate('/dashboard', { replace: true });
    }
  }

  return (
    <div className="min-h-screen bg-slate-950 px-4 py-12">
      <div className="mx-auto max-w-2xl">
        <ProgressIndicator step={step} />

        <div className="rounded-3xl border border-white/10 bg-slate-900/50 p-6 shadow-xl sm:p-8">
          {step === 0 && <StepWelcome onNext={() => setStep(1)} />}
          {step === 1 && (
            <StepTeam
              onNext={() => setStep(2)}
              onSkip={skipOnboarding}
              onTeamCreated={(id) => setCreatedTeamId(id)}
            />
          )}
          {step === 2 && (
            <StepTrello
              isTrelloConnected={isTrelloConnected}
              createdTeamId={createdTeamId}
              onConnect={handleConnectTrello}
              onNext={() => setStep(3)}
              onRefreshStatus={() => queryClient.invalidateQueries({ queryKey: ['trello-status'] })}
            />
          )}
          {step === 3 && (
            <StepReady createdTeamId={createdTeamId} onFinish={finishOnboarding} />
          )}
        </div>
      </div>
    </div>
  );
}
