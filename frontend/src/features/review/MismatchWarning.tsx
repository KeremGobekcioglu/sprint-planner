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
        <p className="text-xs font-bold uppercase tracking-wide text-ink/60">{warning.type}</p>
        <p className="text-sm text-ink">{warning.message}</p>
      </div>
      <button
        className="rounded-md border border-ink/20 px-2 py-1 text-xs font-semibold"
        onClick={() => onDismiss(warning.type)}
      >
        {dismissed ? 'Acknowledged' : 'Acknowledge'}
      </button>
    </div>
  );
}
