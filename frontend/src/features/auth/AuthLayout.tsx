import type { PropsWithChildren, ReactNode } from 'react';

interface AuthLayoutProps extends PropsWithChildren {
  title: string;
  subtitle: string;
  footer: ReactNode;
}

export function AuthLayout({ title, subtitle, footer, children }: AuthLayoutProps) {
  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <div className="w-full max-w-md rounded-2xl border border-white/10 bg-slate-900/70 p-6 shadow-card backdrop-blur-xl sm:p-8">
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-bold text-slate-100">{title}</h1>
          <p className="mt-2 text-sm text-slate-300">{subtitle}</p>
        </div>
        <div className="space-y-4">{children}</div>
        <div className="mt-6 text-center text-sm text-slate-300">{footer}</div>
      </div>
    </div>
  );
}


