import type { SprintConfidenceForecast } from '../../types/planning';

interface ConfidenceForecastProps {
  forecast: SprintConfidenceForecast;
}

export function ConfidenceForecast({ forecast }: ConfidenceForecastProps) {
  const completionPct = Math.round(forecast.historicalCompletionRate * 100);
  const isHealthy = forecast.recommendedDeferPoints === 0;
  const isWarning = forecast.recommendedDeferPoints > 0 && forecast.recommendedDeferPoints <= 10;
  const isCritical = forecast.recommendedDeferPoints > 10;

  return (
    <div className={`rounded-2xl border p-5 space-y-4 ${
      isHealthy ? 'border-emerald-500/30 bg-emerald-950/20' :
      isWarning ? 'border-amber-500/30 bg-amber-950/20' :
      'border-rose-500/30 bg-rose-950/20'
    }`}>
      <div className="flex items-center gap-2">
        <span className="text-xl">{isHealthy ? '✅' : isWarning ? '⚠️' : '🚨'}</span>
        <h3 className="font-semibold text-slate-100">Sprint Confidence Forecast</h3>
        {forecast.sprintsAnalyzed > 0 && (
          <span className="text-xs text-slate-400">
            based on {forecast.sprintsAnalyzed} sprint{forecast.sprintsAnalyzed === 1 ? '' : 's'}
          </span>
        )}
      </div>

      {/* Key numbers */}
      <div className="grid grid-cols-3 gap-3">
        <div className="rounded-xl bg-slate-900/60 p-3 text-center">
          <p className="text-2xl font-bold text-slate-100">{forecast.commitmentPoints}</p>
          <p className="text-xs text-slate-400">committed pts</p>
        </div>
        <div className="rounded-xl bg-slate-900/60 p-3 text-center">
          <p className={`text-2xl font-bold ${isHealthy ? 'text-emerald-400' : 'text-amber-400'}`}>
            {forecast.realisticCompletionPoints}
          </p>
          <p className="text-xs text-slate-400">realistic pts</p>
        </div>
        <div className="rounded-xl bg-slate-900/60 p-3 text-center">
          <p className="text-2xl font-bold text-slate-100">{completionPct}%</p>
          <p className="text-xs text-slate-400">historical rate</p>
        </div>
      </div>

      {/* Progress bar */}
      <div>
        <div className="flex justify-between text-xs text-slate-400 mb-1">
          <span>Realistic completion</span>
          <span>{forecast.realisticCompletionPoints} / {forecast.commitmentPoints} pts</span>
        </div>
        <div className="h-2 rounded-full bg-slate-700">
          <div
            className={`h-2 rounded-full transition-all ${
              isHealthy ? 'bg-emerald-400' : isWarning ? 'bg-amber-400' : 'bg-rose-400'
            }`}
            style={{ width: `${Math.min(100, forecast.commitmentPoints > 0 ? (forecast.realisticCompletionPoints / forecast.commitmentPoints) * 100 : 0)}%` }}
          />
        </div>
      </div>

      {/* Summary */}
      <p className="text-sm text-slate-300">{forecast.forecastSummary}</p>

      {/* Insights */}
      {forecast.insights.length > 0 && (
        <ul className="space-y-1">
          {forecast.insights.map((insight, i) => (
            <li key={i} className="flex items-start gap-2 text-sm text-slate-400">
              <span className="mt-0.5 text-slate-500">→</span>
              {insight}
            </li>
          ))}
        </ul>
      )}

      {/* Defer recommendation */}
      {forecast.recommendedDeferPoints > 0 && (
        <div className="rounded-lg border border-amber-500/20 bg-amber-950/30 px-4 py-3">
          <p className="text-sm font-medium text-amber-300">
            💡 Consider deferring {forecast.recommendedDeferPoints} points on the Review screen
            to align with your team's realistic capacity.
          </p>
        </div>
      )}
    </div>
  );
}
