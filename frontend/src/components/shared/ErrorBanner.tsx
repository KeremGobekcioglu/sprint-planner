interface ErrorBannerProps {
  title?: string;
  message: string;
}

export function ErrorBanner({ title = 'Something went wrong', message }: ErrorBannerProps) {
  return (
    <div className="rounded-xl border border-red-300 bg-red-50 p-4 text-red-900">
      <p className="font-semibold">{title}</p>
      <p className="mt-1 text-sm">{message}</p>
    </div>
  );
}
