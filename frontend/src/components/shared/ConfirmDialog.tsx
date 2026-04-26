interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-md rounded-2xl bg-slate-900/65 p-5 shadow-card">
        <h2 className="text-lg font-bold text-slate-100">{title}</h2>
        <p className="mt-2 text-sm text-slate-300">{message}</p>
        <div className="mt-5 flex justify-end gap-3">
          <button className="rounded-lg border border-slate-500/40 bg-slate-800/50 px-4 py-2 text-sm text-slate-200" onClick={onCancel}>
            {cancelLabel}
          </button>
          <button className="rounded-lg bg-emerald-500/85 px-4 py-2 text-sm font-semibold text-slate-950" onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
