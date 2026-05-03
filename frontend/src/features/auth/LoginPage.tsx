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
      navigate('/teams', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  const loginMutation = useMutation({
    mutationFn: () => login(email, password),
    onSuccess: (data) => {
      loginStore(data.token, data.userId, data.email);
      navigate('/teams');
    },
    onError: (err: unknown) => {
      if (isAxiosError(err)) {
        setError((err.response?.data as { message?: string } | undefined)?.message ?? 'Login failed. Please try again.');
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
      title="Welcome Back"
      subtitle="Sign in to continue sprint planning"
      footer={
        <>
          No account?{' '}
          <button onClick={() => navigate('/signup')} className="text-sky-300 underline hover:text-sky-200">
            Sign up
          </button>
        </>
      }
    >
      {error && <ErrorBanner message={error} />}

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-200">Email</label>
        <input
          type="email"
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && email && password) {
              handleSubmit();
            }
          }}
          disabled={loginMutation.isPending}
          className="w-full rounded-lg border border-slate-600/70 px-3 py-2 text-slate-100 focus:outline-none focus:ring-2 focus:ring-sky-400 disabled:opacity-60"
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-200">Password</label>
        <input
          type="password"
          placeholder="••••••••"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && email && password) {
              handleSubmit();
            }
          }}
          disabled={loginMutation.isPending}
          className="w-full rounded-lg border border-slate-600/70 px-3 py-2 text-slate-100 focus:outline-none focus:ring-2 focus:ring-sky-400 disabled:opacity-60"
        />
      </div>

      <button
        onClick={handleSubmit}
        disabled={loginMutation.isPending || !email || !password}
        className="w-full rounded-lg bg-sky-500 py-2 font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:bg-sky-400"
      >
        {loginMutation.isPending ? 'Logging in...' : 'Log In'}
      </button>

    </AuthLayout>
  );
}

