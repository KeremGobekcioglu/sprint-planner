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
    <div className="flex items-center justify-between rounded-xl border border-sky-200 bg-sky-50 p-4 text-sky-900">
      <div>
        <p className="font-semibold">{title}</p>
        <p className="mt-1 text-sm">{message}</p>
      </div>
      {action ? (
        <button
          type="button"
          onClick={action.onClick}
          className="ml-4 shrink-0 rounded-lg bg-sky-500 px-3 py-2 text-sm font-semibold text-white hover:bg-sky-600"
        >
          {action.label}
        </button>
      ) : null}
    </div>
  );
}
