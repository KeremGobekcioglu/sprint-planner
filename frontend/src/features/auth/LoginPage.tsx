import { useEffect, useState } from 'react';
import { isAxiosError } from 'axios';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { login } from '../../api/authApi';
import { useAuthStore } from '../../store/authStore';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { AuthLayout } from './AuthLayout';

export function LoginPage() {
  const navigate = useNavigate();
  const { login: loginStore, isAuthenticated } = useAuthStore();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  const loginMutation = useMutation({
    mutationFn: () => login(email, password),
    onSuccess: (data) => {
      loginStore(data.token, data.userId, data.email, data.name, data.role);
      localStorage.removeItem(`onboarding_pending_${data.userId}`);
      navigate('/dashboard', { replace: true });
    },
    onError: (err: unknown) => {
      if (isAxiosError(err)) {
        setError(
            (err.response?.data as { message?: string } | undefined)?.message ??
            'Login failed. Please try again.',
        );
        return;
      }
      setError('Login failed. Please try again.');
    },
  });

  const handleSubmit = () => {
    setError(null);
    loginMutation.mutate();
  };

  return (
      <AuthLayout
          variant="login"
          title="Welcome back"
          subtitle="Sign in to continue sprint planning"
          footer={
            <>
              No account yet?{' '}
              <button
                  onClick={() => navigate('/signup')}
                  className="text-indigo-400 transition-colors hover:text-indigo-300"
              >
                Create your workspace →
              </button>
            </>
          }
      >
        {error && <ErrorBanner message={error} />}

        {/* Email */}
        <div>
          <label className="mb-1.5 block text-[11px] font-medium uppercase tracking-widest text-slate-500">
            Email
          </label>
          <input
              type="email"
              placeholder="you@yourteam.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && email && password) handleSubmit();
              }}
              disabled={loginMutation.isPending}
              className="w-full rounded-lg border border-slate-800 bg-slate-900 px-3.5 py-2.5 text-sm text-slate-100 placeholder:text-slate-700 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 disabled:opacity-50"
          />
        </div>

        {/* Password */}
        <div>
          <label className="mb-1.5 block text-[11px] font-medium uppercase tracking-widest text-slate-500">
            Password
          </label>
          <input
              type="password"
              placeholder="••••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && email && password) handleSubmit();
              }}
              disabled={loginMutation.isPending}
              className="w-full rounded-lg border border-slate-800 bg-slate-900 px-3.5 py-2.5 text-sm text-slate-100 placeholder:text-slate-700 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 disabled:opacity-50"
          />
        </div>

        {/* Submit */}
        <button
            onClick={handleSubmit}
            disabled={loginMutation.isPending || !email || !password}
            className="mt-1 flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-500 py-2.5 text-sm font-medium text-white transition-colors hover:bg-indigo-400 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {loginMutation.isPending ? (
              <>
                <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                </svg>
                Signing in…
              </>
          ) : (
              <>
                Sign in to workspace
                <svg className="h-4 w-4" viewBox="0 0 16 16" fill="none">
                  <path d="M3 8h10M9 4l4 4-4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </>
          )}
        </button>
      </AuthLayout>
  );
}