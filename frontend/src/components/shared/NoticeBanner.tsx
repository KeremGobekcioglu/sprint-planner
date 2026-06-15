interface NoticeBannerProps {
  title?: string;
  message: string;
  action?: {
    label: string;
    onClick: () => void;
  };
}

export function NoticeBanner({ title = 'Heads up', message, action }: NoticeBannerProps) {
  return (
    <div className="flex flex-col gap-3 rounded-xl border border-sky-200 bg-sky-50 p-4 text-sky-900 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <p className="font-semibold">{title}</p>
        <p className="mt-1 text-sm">{message}</p>
      </div>
      {action ? (
        <button
          type="button"
          onClick={action.onClick}
          className="min-h-[44px] shrink-0 rounded-lg bg-sky-500 px-3 py-2 text-sm font-semibold text-white hover:bg-sky-600"
        >
          {action.label}
        </button>
      ) : null}
    </div>
  );
}
