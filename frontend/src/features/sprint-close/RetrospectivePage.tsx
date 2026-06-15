import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getRetrospectiveReport } from '../../api/sprintApi';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { LoadingSpinner } from '../../components/shared/LoadingSpinner';
import { PageLayout } from '../../components/shared/PageLayout';

const GOAL_RESULT_BADGE: Record<string, string> = {
  YES: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30',
  PARTIALLY: 'bg-amber-500/20 text-amber-300 border-amber-500/30',
  NO: 'bg-rose-500/20 text-rose-300 border-rose-500/30',
};

const COMPLETION_BAR_COLOR: Record<string, string> = {
  YES: 'bg-emerald-500',
  PARTIALLY: 'bg-amber-500',
  NO: 'bg-rose-500',
};

function trendIcon(trend: string): string {
  if (trend === 'improving') return '📈';
  if (trend === 'declining') return '📉';
  if (trend === 'new to team') return '🆕';
  return '➡️';
}

function rateColor(rate: number): string {
  if (rate >= 0.80) return 'text-emerald-400';
  if (rate >= 0.60) return 'text-amber-400';
  return 'text-rose-400';
}

export function RetrospectivePage() {
  const { sprintId } = useParams();
  const navigate = useNavigate();

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['retrospective', sprintId],
    queryFn: () => getRetrospectiveReport(sprintId!),
    enabled: !!sprintId,
    staleTime: 1000 * 60 * 5,
  });

  const barColor = data ? (COMPLETION_BAR_COLOR[data.goalResult] ?? 'bg-amber-500') : 'bg-amber-500';
  const badgeClass = data ? (GOAL_RESULT_BADGE[data.goalResult] ?? GOAL_RESULT_BADGE['PARTIALLY']) : '';

  return (
    <PageLayout title="Sprint Retrospective" subtitle="AI-powered analysis of your last sprint.">
      <div className="mb-4">
        <button
          type="button"
          onClick={() => navigate('/dashboard')}
          className="rounded-lg border border-slate-500/40 px-3 py-1.5 text-sm font-medium text-slate-300 hover:bg-slate-800/40"
        >
          ← Back to Dashboard
        </button>
      </div>

      {isLoading ? <LoadingSpinner label="Generating retrospective report..." /> : null}
      {isError ? <ErrorBanner message="Could not load retrospective report." error={error} /> : null}

      {data ? (
        <div className="grid gap-6">
          {/* Section 1 — Sprint summary */}
          <section className="space-y-4 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card">
            <div className="flex flex-wrap items-center gap-3">
              <h2 className="text-lg font-semibold text-slate-100">{data.sprintGoal}</h2>
              <span className={`rounded-full border px-2.5 py-0.5 text-xs font-semibold ${badgeClass}`}>
                {data.goalResult}
              </span>
            </div>

            <div className="flex flex-wrap gap-4 text-sm text-slate-400">
              <span>
                Planned: <strong className="text-slate-200">{data.plannedPoints} pts</strong>
              </span>
              <span>
                Completed: <strong className="text-slate-200">{data.completedPoints} pts</strong>
              </span>
              <span>
                Rate:{' '}
                <strong className={rateColor(data.completionRate)}>
                  {Math.round(data.completionRate * 100)}%
                </strong>
              </span>
            </div>

            <div className="h-3 w-full overflow-hidden rounded-full bg-slate-700/60">
              <div
                className={`h-full rounded-full transition-all ${barColor}`}
                style={{ width: `${Math.min(100, Math.round(data.completionRate * 100))}%` }}
              />
            </div>
          </section>

          {/* Section 2 — Developer performance */}
          <section className="space-y-4 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card">
            <h2 className="text-lg font-semibold text-slate-100">Developer Performance</h2>
            {data.developerStats.length === 0 ? (
              <p className="text-sm text-slate-400">No developer data available.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-white/10 text-xs text-slate-400">
                      <th className="pb-2 text-left">Developer</th>
                      <th className="pb-2 text-right">Planned</th>
                      <th className="pb-2 text-right">Completed</th>
                      <th className="pb-2 text-right">Rate</th>
                      <th className="pb-2 text-right">Trend</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/5">
                    {data.developerStats.map((dev) => (
                      <tr key={dev.developerName}>
                        <td className="py-2 font-medium text-slate-200">{dev.developerName}</td>
                        <td className="py-2 text-right text-slate-400">{dev.plannedPts} pts</td>
                        <td className="py-2 text-right text-slate-400">{dev.completedPts} pts</td>
                        <td className={`py-2 text-right font-semibold ${rateColor(dev.completionRate)}`}>
                          {Math.round(dev.completionRate * 100)}%
                        </td>
                        <td className="py-2 text-right text-slate-300">
                          {trendIcon(dev.trend)} {dev.trend}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {/* Section 3 — Patterns and recommendations */}
          {(data.patterns.length > 0 || data.recommendations.length > 0) ? (
            <section className="space-y-4 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card">
              <h2 className="text-lg font-semibold text-slate-100">Insights</h2>
              <div className="grid gap-6 sm:grid-cols-2">
                {data.patterns.length > 0 ? (
                  <div className="space-y-2">
                    <h3 className="text-sm font-semibold text-slate-300">What we observed</h3>
                    <ul className="space-y-1.5">
                      {data.patterns.map((pattern, i) => (
                        <li key={i} className="flex gap-2 text-sm text-slate-400">
                          <span className="mt-0.5 shrink-0 text-amber-400">•</span>
                          <span>{pattern}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}
                {data.recommendations.length > 0 ? (
                  <div className="space-y-2">
                    <h3 className="text-sm font-semibold text-slate-300">What to do next sprint</h3>
                    <ul className="space-y-1.5">
                      {data.recommendations.map((rec, i) => (
                        <li key={i} className="flex gap-2 text-sm text-slate-400">
                          <span className="mt-0.5 shrink-0 text-emerald-400">→</span>
                          <span>{rec}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}
              </div>
            </section>
          ) : null}

          {/* Section 4 — AI analysis */}
          {data.aiAnalysis ? (
            <section className="space-y-4 rounded-3xl border border-white/10 bg-slate-900/50 p-5 shadow-card">
              <h2 className="text-lg font-semibold text-slate-100">🤖 AI Retrospective Analysis</h2>
              <div className="space-y-3 text-sm leading-relaxed text-slate-300">
                {data.aiAnalysis.split('\n\n').filter(Boolean).map((paragraph, i) => (
                  <p key={i}>{paragraph}</p>
                ))}
              </div>
            </section>
          ) : null}
        </div>
      ) : null}
    </PageLayout>
  );
}
