interface LoadingSpinnerProps {
  label?: string;
}

export function LoadingSpinner({ label = 'Loading...' }: LoadingSpinnerProps) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-ink/15 bg-white/70 p-4">
      <div className="h-5 w-5 animate-spin rounded-full border-2 border-ink/20 border-t-ink" />
      <span className="text-sm font-medium text-ink/80">{label}</span>
    </div>
  );
}
