import type { PropsWithChildren, ReactNode } from 'react';
import { Navbar } from './Navbar';

interface PageLayoutProps extends PropsWithChildren {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}

export function PageLayout({ title, subtitle, actions, children }: PageLayoutProps) {
  return (
    <div className="min-h-screen">
      <Navbar />
      <div className="px-4 py-6 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-6xl">
          <header className="mb-6 rounded-3xl border border-white/10 bg-slate-900/55 p-6 shadow-card backdrop-blur-xl">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h1 className="text-3xl font-bold text-slate-100">{title}</h1>
                {subtitle ? <p className="mt-2 text-sm text-slate-300">{subtitle}</p> : null}
              </div>
              {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
            </div>
          </header>
          <main>{children}</main>
        </div>
      </div>
    </div>
  );
}
