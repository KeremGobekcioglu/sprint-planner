interface ErrorBannerProps {
  title?: string;
  message: string;
  /** Raw Axios error — extracts `error` field from backend GlobalExceptionHandler response if present */
  error?: unknown;
}

function extractMessage(error: unknown, fallback: string): string {
  if (!error || typeof error !== 'object') return fallback;
  const axiosError = error as { response?: { data?: { error?: string } } };
  const backendMessage = axiosError.response?.data?.error;
  return backendMessage && typeof backendMessage === 'string' ? backendMessage : fallback;
}

export function ErrorBanner({ title = 'Something went wrong', message, error }: ErrorBannerProps) {
  const displayMessage = error ? extractMessage(error, message) : message;
  return (
    <div className="rounded-xl border border-red-300 bg-red-50 p-4 text-red-900">
      <p className="font-semibold">{title}</p>
      <p className="mt-1 text-sm">{displayMessage}</p>
    </div>
  );
}
