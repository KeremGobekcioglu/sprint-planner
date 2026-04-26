import type { MismatchWarning as Warning } from '../../types/planning';

interface MismatchWarningProps {
  warning: Warning;
  dismissed: boolean;
  onDismiss: (type: Warning['type']) => void;
}

export function MismatchWarning({ warning, dismissed, onDismiss }: MismatchWarningProps) {
  return (
    <div
      className={`flex items-start justify-between gap-3 rounded-xl border p-3 ${
        dismissed ? 'border-pine/30 bg-emerald-50' : 'border-amber-300 bg-amber-50'
      }`}
    >
      <div>
        <p className="text-xs font-bold uppercase tracking-wide text-slate-300">{warning.type}</p>
        <p className="text-sm text-slate-100">{warning.message}</p>
      </div>
      <button
        className="rounded-md border border-slate-500/40 bg-slate-800/50 px-2 py-1 text-xs font-semibold text-slate-200"
        onClick={() => onDismiss(warning.type)}
      >
        {dismissed ? 'Acknowledged' : 'Acknowledge'}
      </button>
    </div>
  );
}
