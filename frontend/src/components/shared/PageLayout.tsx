import type { PropsWithChildren, ReactNode } from 'react';
 import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../../store/authStore';

interface PageLayoutProps extends PropsWithChildren {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}

export function PageLayout({ title, subtitle, actions, children }: PageLayoutProps) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { isAuthenticated, logout } = useAuthStore();

  const handleLogout = () => {
    logout();
    queryClient.clear();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <header className="mb-6 rounded-3xl border border-white/10 bg-slate-900/55 p-6 shadow-card backdrop-blur-xl">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h1 className="text-3xl font-bold text-slate-100">{title}</h1>
              {subtitle ? <p className="mt-2 text-sm text-slate-300">{subtitle}</p> : null}
            </div>
            <div className="flex items-center gap-2">
              {actions}
              {isAuthenticated ? (
                <button
                  type="button"
                  onClick={handleLogout}
                  className="rounded-lg border border-slate-400/40 px-3 py-2 text-sm font-semibold text-slate-100 hover:bg-slate-700/40"
                >
                  Log out
                </button>
              ) : null}
            </div>
          </div>
        </header>
        <main>{children}</main>
      </div>
    </div>
  );
}
