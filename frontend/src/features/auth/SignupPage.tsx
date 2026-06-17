import { useEffect, useRef, useState } from 'react';
import { isAxiosError } from 'axios';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { register } from '../../api/authApi';
import { useAuthStore } from '../../store/authStore';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { AuthLayout } from './AuthLayout';

export function SignupPage() {
  const navigate = useNavigate();
  const { login: loginStore, isAuthenticated } = useAuthStore();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const hasCompletedSignupFlow = useRef(false);

  const signupMutation = useMutation({
    mutationFn: () => register(email, password),
    onSuccess: (data) => {
      hasCompletedSignupFlow.current = true;
      loginStore(data.token, data.userId, data.email, data.name, data.role);
      localStorage.setItem(`onboarding_pending_${data.userId}`, 'true');
      navigate('/onboarding', { replace: true });
    },
    onError: (err: unknown) => {
      if (isAxiosError(err)) {
        setError(
            (err.response?.data as { message?: string } | undefined)?.message ??
            'Signup failed. Please try again.',
        );
        return;
      }
      setError('Signup failed. Please try again.');
    },
  });

  useEffect(() => {
    if (isAuthenticated && !hasCompletedSignupFlow.current) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  const handleSignup = () => {
    setError(null);
    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }
    if (password.length < 6) {
      setError('Password must be at least 6 characters');
      return;
    }
    signupMutation.mutate();
  };

  return (
      <AuthLayout
          variant="signup"
          title="Create your workspace"
          subtitle="Get your team planning in under two minutes"
          footer={
            <>
              Already have an account?{' '}
              <button
                  onClick={() => navigate('/login')}
                  className="text-indigo-400 transition-colors hover:text-indigo-300"
              >
                Sign in →
              </button>
            </>
          }
      >
        {error && <ErrorBanner message={error} />}

        {/* Email */}
        <div>
          <label className="mb-1.5 block text-[11px] font-medium uppercase tracking-widest text-slate-500">
            Work email
          </label>
          <input
              type="email"
              placeholder="you@yourteam.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={signupMutation.isPending}
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
              placeholder="At least 6 characters"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={signupMutation.isPending}
              className="w-full rounded-lg border border-slate-800 bg-slate-900 px-3.5 py-2.5 text-sm text-slate-100 placeholder:text-slate-700 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 disabled:opacity-50"
          />
        </div>

        {/* Confirm password */}
        <div>
          <label className="mb-1.5 block text-[11px] font-medium uppercase tracking-widest text-slate-500">
            Confirm password
          </label>
          <input
              type="password"
              placeholder="Repeat password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && email && password && confirmPassword) handleSignup();
              }}
              disabled={signupMutation.isPending}
              className="w-full rounded-lg border border-slate-800 bg-slate-900 px-3.5 py-2.5 text-sm text-slate-100 placeholder:text-slate-700 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 disabled:opacity-50"
          />
        </div>

        {/* Submit */}
        <button
            onClick={handleSignup}
            disabled={signupMutation.isPending || !email || !password || !confirmPassword}
            className="mt-1 flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-500 py-2.5 text-sm font-medium text-white transition-colors hover:bg-indigo-400 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {signupMutation.isPending ? (
              <>
                <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                </svg>
                Creating workspace…
              </>
          ) : (
              <>
                Create workspace
                <svg className="h-4 w-4" viewBox="0 0 16 16" fill="none">
                  <path d="M8 2l6 6-6 6M2 8h12" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </>
          )}
        </button>
      </AuthLayout>
  );
}