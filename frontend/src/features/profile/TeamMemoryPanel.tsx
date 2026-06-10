import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { ErrorBanner } from '../../components/shared/ErrorBanner';
import { LoadingSpinner } from '../../components/shared/LoadingSpinner';
import { useTeamMemory } from '../../hooks/useTeamMemory';
import { clearTeamProfile } from '../../api/profileApi';

interface TeamMemoryPanelProps {
  teamId: string;
}

const PROSE =
  'prose prose-invert prose-sm max-w-none ' +
  'prose-headings:text-slate-200 prose-headings:font-semibold ' +
  'prose-p:text-slate-300 prose-p:leading-relaxed ' +
  'prose-strong:text-slate-200 ' +
  'prose-table:text-xs prose-table:w-full ' +
  'prose-th:text-slate-300 prose-th:font-semibold prose-th:border prose-th:border-slate-700 prose-th:px-2 prose-th:py-1 ' +
  'prose-td:text-slate-400 prose-td:border prose-td:border-slate-700 prose-td:px-2 prose-td:py-1 ' +
  'prose-li:text-slate-300 ' +
  'prose-code:text-slate-300 prose-code:bg-slate-800 prose-code:px-1 prose-code:rounded';

export function TeamMemoryPanel({ teamId }: TeamMemoryPanelProps) {
  const [activeTab, setActiveTab] = useState<'preferences' | 'history'>('history');
  const queryClient = useQueryClient();
  const { data, isLoading, isError } = useTeamMemory(teamId);

  const clearMutation = useMutation({
    mutationFn: () => clearTeamProfile(teamId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['team-memory', teamId] });
    },
  });

  return (
    <div className="space-y-3">
      {/* Tab switcher */}
      <div className="flex w-fit gap-1 rounded-lg border border-white/10 bg-slate-950/40 p-1">
        <button
          type="button"
          onClick={() => setActiveTab('preferences')}
          className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
            activeTab === 'preferences'
              ? 'bg-slate-700 text-slate-100'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          🎛 Team Preferences
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('history')}
          className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
            activeTab === 'history'
              ? 'bg-slate-700 text-slate-100'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          📋 Sprint History
        </button>
      </div>

      {/* Clear button — preferences tab only */}
      {activeTab === 'preferences' && data && (
        <div className="flex justify-end">
          <button
            type="button"
            onClick={() => clearMutation.mutate()}
            disabled={clearMutation.isPending || !data.teamProfile}
            className="text-xs text-rose-400 transition-colors hover:text-rose-300 disabled:opacity-40"
          >
            {clearMutation.isPending ? 'Clearing...' : 'Clear preferences'}
          </button>
        </div>
      )}

      {/* Content panel */}
      <div className="min-h-[120px] rounded-xl border border-white/10 bg-slate-950/40 p-4">
        {isLoading ? (
          <LoadingSpinner label="Loading memory..." />
        ) : isError ? (
          <ErrorBanner message="Could not load memory files." />
        ) : activeTab === 'preferences' ? (
          data?.teamProfile ? (
            <div className={PROSE}>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{data.teamProfile}</ReactMarkdown>
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center py-8 text-center">
              <p className="text-sm text-slate-400">No preferences learned yet.</p>
              <p className="mt-1 text-xs text-slate-500">
                Complete a sprint review to start building team preferences.
              </p>
            </div>
          )
        ) : data?.sprintHistory ? (
          <div className={PROSE}>
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{data.sprintHistory}</ReactMarkdown>
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center py-8 text-center">
            <p className="text-sm text-slate-400">No sprint history yet.</p>
            <p className="mt-1 text-xs text-slate-500">Close your first sprint to build history.</p>
          </div>
        )}
      </div>

      {/* Last updated */}
      {data && (
        <p className="text-xs text-slate-600">
          {activeTab === 'preferences' && data.teamProfileLastUpdated
            ? `Preferences updated ${new Date(data.teamProfileLastUpdated).toLocaleDateString()}`
            : activeTab === 'history' && data.sprintHistoryLastUpdated
              ? `History updated ${new Date(data.sprintHistoryLastUpdated).toLocaleDateString()}`
              : null}
        </p>
      )}
    </div>
  );
}
