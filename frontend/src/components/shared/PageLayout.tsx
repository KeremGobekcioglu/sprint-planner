import type { PropsWithChildren, ReactNode } from 'react';

interface PageLayoutProps extends PropsWithChildren {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}

export function PageLayout({ title, subtitle, actions, children }: PageLayoutProps) {
  return (
    <div className="min-h-screen px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <header className="mb-6 rounded-3xl border border-black/10 bg-white/80 p-6 shadow-card backdrop-blur">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h1 className="text-3xl font-bold text-ink">{title}</h1>
              {subtitle ? <p className="mt-2 text-sm text-ink/70">{subtitle}</p> : null}
            </div>
            {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
          </div>
        </header>
        <main>{children}</main>
      </div>
    </div>
  );
}
