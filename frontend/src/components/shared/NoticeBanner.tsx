interface NoticeBannerProps {
  title?: string;
  message: string;
}

export function NoticeBanner({ title = 'Heads up', message }: NoticeBannerProps) {
  return (
    <div className="rounded-xl border border-sky-200 bg-sky-50 p-4 text-sky-900">
      <p className="font-semibold">{title}</p>
      <p className="mt-1 text-sm">{message}</p>
    </div>
  );
}

