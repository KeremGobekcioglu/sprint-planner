interface TaskCompletionToggleProps {
  taskId: string;
  title: string;
  checked: boolean;
  onChange: (taskId: string, checked: boolean) => void;
}

export function TaskCompletionToggle({ taskId, title, checked, onChange }: TaskCompletionToggleProps) {
  return (
    <label className="flex items-center justify-between gap-3 rounded-lg border border-ink/10 bg-white p-3">
      <span className="text-sm text-ink">{title}</span>
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(taskId, event.target.checked)}
        className="h-4 w-4 accent-pine"
      />
    </label>
  );
}
