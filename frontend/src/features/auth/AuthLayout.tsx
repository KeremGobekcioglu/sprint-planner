import type { PropsWithChildren, ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';

// ─── Logo ────────────────────────────────────────────────────────────────────

function LogoMark() {
  return (
      <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-500">
        <div className="flex flex-col gap-[3px]">
          <div className="h-[2px] w-4 rounded-sm bg-white" />
          <div className="h-[2px] w-3 rounded-sm bg-white" />
          <div className="h-[2px] w-2 rounded-sm bg-white" />
        </div>
      </div>
  );
}

// ─── Animated pulse line (sprint rhythm signature) ───────────────────────────

function PulseLine() {
  return (
      <div className="relative mb-7 h-[2px] overflow-hidden rounded-full bg-slate-800">
        <div className="animate-pulse-sweep absolute inset-y-0 w-[40%] bg-gradient-to-r from-transparent via-indigo-500 to-transparent" />
      </div>
  );
}

// ─── Left panel variants ─────────────────────────────────────────────────────

interface LeftPanelProps {
  variant: 'login' | 'signup';
}

function LeftPanel({ variant }: LeftPanelProps) {
  const isLogin = variant === 'login';

  return (
      <div className="relative hidden flex-col justify-between overflow-hidden border-r border-slate-800 bg-[#0D1428] p-10 lg:flex lg:w-[420px] lg:flex-shrink-0">
        {/* Ambient glow */}
        <div className="pointer-events-none absolute -left-16 -top-16 h-72 w-72 rounded-full bg-indigo-500/[0.06]" />

        {/* Logo */}
        <div className="flex items-center gap-2.5">
          <LogoMark />
          <span className="font-display text-[17px] font-bold tracking-tight text-slate-100">
          Sprint Planner
        </span>
        </div>

        {/* Copy */}
        <div className="flex flex-col gap-5">
          {isLogin ? (
              <>
                <h2 className="font-display text-[28px] font-bold leading-[1.2] text-slate-100">
                  Your team&apos;s{' '}
                  <span className="text-indigo-400">velocity,</span>
                  <br />
                  refined by AI.
                </h2>
                <p className="max-w-[260px] text-sm leading-relaxed text-slate-400">
                  Pick up where your last sprint left off. Your team memory, task history,
                  and planning context are all waiting.
                </p>

                {/* Stats */}
                <div className="grid grid-cols-2 gap-2.5">
                  <div className="rounded-lg border border-slate-800 bg-white/[0.03] p-3">
                    <div className="font-display text-[22px] font-bold text-slate-100">
                      74<span className="ml-0.5 text-[13px] text-indigo-400">pts</span>
                    </div>
                    <div className="mt-0.5 text-[11px] uppercase tracking-widest text-slate-600">
                      Avg velocity
                    </div>
                  </div>
                  <div className="rounded-lg border border-slate-800 bg-white/[0.03] p-3">
                    <div className="font-display text-[22px] font-bold text-slate-100">
                      12<span className="ml-0.5 text-[13px] text-indigo-400">sp</span>
                    </div>
                    <div className="mt-0.5 text-[11px] uppercase tracking-widest text-slate-600">
                      Last sprint
                    </div>
                  </div>
                </div>

                {/* Velocity track */}
                <div>
                  <div className="mb-1.5 flex justify-between text-[11px] uppercase tracking-widest text-slate-600">
                    <span>Sprint capacity</span>
                    <span>74%</span>
                  </div>
                  <div className="h-[3px] overflow-hidden rounded-full bg-slate-800">
                    <div className="animate-grow h-full rounded-full bg-indigo-500" />
                  </div>
                  {/* Mini bar chart */}
                  <div className="mt-3 flex h-9 items-end gap-1">
                    {[40, 55, 50, 65, 60, 75, 70, 85].map((h, i) => (
                        <div
                            key={i}
                            className={`flex-1 rounded-t-[2px] ${
                                i === 7 ? 'bg-teal-400' : i === 6 ? 'bg-indigo-500' : 'bg-indigo-500/20'
                            }`}
                            style={{ height: `${h}%` }}
                        />
                    ))}
                  </div>
                </div>
              </>
          ) : (
              <>
                <h2 className="font-display text-[28px] font-bold leading-[1.2] text-slate-100">
                  Set up your{' '}
                  <span className="text-indigo-400">AI planning</span>
                  <br />
                  workspace.
                </h2>
                <p className="max-w-[260px] text-sm leading-relaxed text-slate-400">
                  Get running in under two minutes. Connect your team, define your first
                  sprint, and let AI handle the estimation.
                </p>

                {/* Stats */}
                <div className="grid grid-cols-2 gap-2.5">
                  <div className="rounded-lg border border-slate-800 bg-white/[0.03] p-3">
                    <div className="font-display text-[22px] font-bold text-slate-100">
                      4<span className="ml-0.5 text-[13px] text-indigo-400">agents</span>
                    </div>
                    <div className="mt-0.5 text-[11px] uppercase tracking-widest text-slate-600">
                      AI pipeline
                    </div>
                  </div>
                  <div className="rounded-lg border border-slate-800 bg-white/[0.03] p-3">
                    <div className="font-display text-[22px] font-bold text-slate-100">
                      ~2<span className="ml-0.5 text-[13px] text-indigo-400">min</span>
                    </div>
                    <div className="mt-0.5 text-[11px] uppercase tracking-widest text-slate-600">
                      Setup time
                    </div>
                  </div>
                </div>

                {/* Checklist */}
                <div className="flex flex-col gap-2">
                  {[
                    'Free to start, no credit card',
                    'Trello integration included',
                    'AI memory learns your team',
                  ].map((item) => (
                      <div key={item} className="flex items-center gap-2.5 text-sm text-slate-400">
                        <div className="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full bg-teal-400/15">
                          <svg className="h-2.5 w-2.5 text-teal-400" viewBox="0 0 10 10" fill="none">
                            <path d="M2 5l2 2 4-4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                          </svg>
                        </div>
                        {item}
                      </div>
                  ))}
                </div>
              </>
          )}
        </div>

        {/* Spacer */}
        <div />
      </div>
  );
}

// ─── AuthLayout ───────────────────────────────────────────────────────────────

interface AuthLayoutProps extends PropsWithChildren {
  title: string;
  subtitle: string;
  footer: ReactNode;
  variant: 'login' | 'signup';
}

export function AuthLayout({ title, subtitle, footer, variant, children }: AuthLayoutProps) {
  const navigate = useNavigate();

  return (
      <div className="flex min-h-screen bg-[#0A0F1E]">
        <LeftPanel variant={variant} />

        {/* Right panel */}
        <div className="flex flex-1 items-center justify-center px-6 py-12">
          <div className="w-full max-w-[360px]">
            {/* Mobile logo */}
            <div
                className="mb-8 flex cursor-pointer items-center gap-2.5 lg:hidden"
                onClick={() => navigate('/')}
            >
              <LogoMark />
              <span className="font-display text-[17px] font-bold tracking-tight text-slate-100">
              Sprint Planner
            </span>
            </div>

            {/* Heading */}
            <div className="mb-7">
              <h1 className="font-display text-[22px] font-bold text-slate-100">{title}</h1>
              <div className="mt-1.5 text-sm text-slate-400">{subtitle}</div>
            </div>

            <PulseLine />

            {/* Form fields */}
            <div className="space-y-4">{children}</div>

            {/* Footer link */}
            <div className="mt-6 text-center text-sm text-slate-500">{footer}</div>

            {/* Back to home */}
            <div className="mt-4 text-center">
              <button
                  onClick={() => navigate('/')}
                  className="text-xs text-slate-600 transition-colors hover:text-slate-400"
              >
                ← Back to homepage
              </button>
            </div>
          </div>
        </div>
      </div>
  );
}