import { useEffect, useState } from 'react';
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

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  const signupMutation = useMutation({
    mutationFn: () => register(email, password),
    onSuccess: (data) => {
      loginStore(data.token, data.userId, data.email, data.name, data.role);
      navigate('/dashboard', { replace: true });
    },
    onError: (err: unknown) => {
      if (isAxiosError(err)) {
        setError((err.response?.data as { message?: string } | undefined)?.message ?? 'Signup failed. Please try again.');
        return;
      }
      setError('Signup failed. Please try again.');
    },
  });

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
      title="Create Account"
      subtitle="Start planning with your team"
      footer={
        <>
          Already have an account?{' '}
          <button onClick={() => navigate('/login')} className="text-sky-300 underline hover:text-sky-200">
            Log in
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
          disabled={signupMutation.isPending}
          className="w-full rounded-lg border border-slate-600/70 px-3 py-2 text-slate-100 focus:outline-none focus:ring-2 focus:ring-sky-400 disabled:opacity-60"
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-200">Password</label>
        <input
          type="password"
          placeholder="At least 6 characters"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          disabled={signupMutation.isPending}
          className="w-full rounded-lg border border-slate-600/70 px-3 py-2 text-slate-100 focus:outline-none focus:ring-2 focus:ring-sky-400 disabled:opacity-60"
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-200">Confirm Password</label>
        <input
          type="password"
          placeholder="Repeat password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && email && password && confirmPassword) {
              handleSignup();
            }
          }}
          disabled={signupMutation.isPending}
          className="w-full rounded-lg border border-slate-600/70 px-3 py-2 text-slate-100 focus:outline-none focus:ring-2 focus:ring-sky-400 disabled:opacity-60"
        />
      </div>

      <button
        onClick={handleSignup}
        disabled={signupMutation.isPending || !email || !password || !confirmPassword}
        className="w-full rounded-lg bg-sky-500 py-2 font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:bg-sky-400"
      >
        {signupMutation.isPending ? 'Signing up...' : 'Sign Up'}
      </button>

    </AuthLayout>
  );
}
