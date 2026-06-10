import { useNavigate } from 'react-router-dom';

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

const FEATURES = [
    {
        icon: (
            <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2V9M9 21H5a2 2 0 01-2-2V9m0 0h18" />
            </svg>
        ),
        color: 'bg-indigo-500/10 text-indigo-400',
        title: 'AI breakdown pipeline',
        desc: 'Four-stage AI pipeline turns rough epics into estimated, distributed sprint tasks in seconds.',
    },
    {
        icon: (
            <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M3.055 11H5a2 2 0 012 2v1a2 2 0 002 2 2 2 0 012 2v2.945M8 3.935V5.5A2.5 2.5 0 0010.5 8h.5a2 2 0 012 2 2 2 0 004 0 2 2 0 012-2h1.064M15 20.488V18a2 2 0 012-2h3.064" />
            </svg>
        ),
        color: 'bg-teal-400/10 text-teal-400',
        title: 'Team memory',
        desc: "Learns your team's velocity and preferences across sprints to produce smarter estimates over time.",
    },
    {
        icon: (
            <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <rect x="3" y="3" width="7" height="7" rx="1" strokeLinecap="round" />
                <rect x="14" y="3" width="7" height="7" rx="1" strokeLinecap="round" />
                <rect x="3" y="14" width="7" height="7" rx="1" strokeLinecap="round" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M14 17.5h7M17.5 14v7" />
            </svg>
        ),
        color: 'bg-indigo-500/10 text-indigo-400',
        title: 'Trello sync',
        desc: 'Publish approved sprint plans directly to Trello boards with one click — no copy-pasting.',
    },
];

export function LandingPage() {
    const navigate = useNavigate();

    return (
        <div className="flex min-h-screen flex-col bg-[#0A0F1E]">
            {/* Nav */}
            <header className="flex items-center justify-between border-b border-slate-800 px-8 py-4">
                <div className="flex items-center gap-2.5">
                    <LogoMark />
                    <span className="font-display text-[17px] font-bold tracking-tight text-slate-100">
            Sprint Planner
          </span>
                </div>
                <div className="flex items-center gap-2">
                    <button
                        onClick={() => navigate('/login')}
                        className="rounded-lg border border-slate-800 px-4 py-2 text-sm text-slate-400 transition-colors hover:border-indigo-500/50 hover:text-slate-200"
                    >
                        Sign in
                    </button>
                    <button
                        onClick={() => navigate('/signup')}
                        className="rounded-lg bg-indigo-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-indigo-400"
                    >
                        Get started
                    </button>
                </div>
            </header>

            {/* Hero */}
            <main className="flex flex-1 flex-col items-center justify-center px-6 pb-0 pt-16 text-center">
                {/* Eyebrow */}
                <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-indigo-500/30 bg-indigo-500/10 px-4 py-1.5 text-xs font-medium tracking-wide text-indigo-300">
                    <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-teal-400" />
                    AI-assisted sprint planning
                </div>

                <h1 className="font-display mb-5 max-w-[560px] text-[42px] font-bold leading-[1.15] text-slate-100">
                    Plan smarter sprints.{' '}
                    <span className="text-indigo-400">Ship with confidence.</span>
                </h1>

                <p className="mb-8 max-w-[460px] text-[15px] leading-relaxed text-slate-400">
                    Sprint Planner uses AI to break down epics, estimate tasks, and distribute
                    work across your team — so your standups stay focused and your velocity
                    stays real.
                </p>

                <div className="flex gap-3">
                    <button
                        onClick={() => navigate('/signup')}
                        className="rounded-xl bg-indigo-500 px-7 py-3 text-[15px] font-medium text-white transition-colors hover:bg-indigo-400"
                    >
                        Start planning for free
                    </button>
                    <button
                        onClick={() => navigate('/login')}
                        className="rounded-xl border border-slate-800 px-7 py-3 text-[15px] text-slate-400 transition-colors hover:border-slate-600 hover:text-slate-200"
                    >
                        Sign into your workspace
                    </button>
                </div>
            </main>

            {/* Feature grid */}
            <div className="mt-16 grid grid-cols-1 divide-y divide-slate-800 border-t border-slate-800 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
                {FEATURES.map((f) => (
                    <div key={f.title} className="flex flex-col gap-3 px-8 py-7">
                        <div className={`flex h-9 w-9 items-center justify-center rounded-lg ${f.color}`}>
                            {f.icon}
                        </div>
                        <div className="font-display text-sm font-semibold text-slate-200">{f.title}</div>
                        <div className="text-sm leading-relaxed text-slate-500">{f.desc}</div>
                    </div>
                ))}
            </div>
        </div>
    );
}