import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { saveTrelloToken } from '../../api/profileApi';

export function TrelloCallbackPage() {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fragment = window.location.hash;
    const params = new URLSearchParams(fragment.startsWith('#') ? fragment.slice(1) : fragment);
    const token = params.get('token');

    if (!token) {
      setError('Trello authorization failed. Please try again.');
      return;
    }

    saveTrelloToken(token)
      .then(() => {
        navigate('/profile', { state: { trelloConnected: true }, replace: true });
      })
      .catch(() => {
        setError('Failed to save Trello token. Please try again.');
      });
  }, [navigate]);

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-950 text-slate-100">
        <div className="rounded-2xl border border-rose-500/30 bg-slate-900 p-8 text-center">
          <p className="text-rose-400">{error}</p>
          <button
            type="button"
            onClick={() => navigate('/profile')}
            className="mt-4 rounded-lg bg-slate-700 px-4 py-2 text-sm hover:bg-slate-600"
          >
            Back to Profile
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-950 text-slate-400">
      <p>Connecting Trello...</p>
    </div>
  );
}
