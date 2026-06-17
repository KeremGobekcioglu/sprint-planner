import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import type { Task } from '../../types/sprint';
import { HelpTooltip } from '../../components/shared/HelpTooltip';
import { getTaskGuidance } from '../../api/sprintApi';

interface TaskCardProps {
  task: Task;
  onEdit: (taskId: string, field: string, oldValue: string | number, newValue: string | number) => void;
  onDefer: (taskId: string) => void;
  isDeferred: boolean;
  sprintId: string;
  isGuidanceOpen: boolean;
  onGuidanceToggle: (taskId: string | null) => void;
  readOnly?: boolean;
}

interface TaskCardFormValues {
  title: string;
  acceptanceCriterion: string;
  storyPoints: number;
}

interface GuidanceData {
  hints: string;
  suggestedApproach: string;
  riskFlags: string[];
}

export function TaskCard({ task, onEdit, onDefer, isDeferred, sprintId, isGuidanceOpen, onGuidanceToggle, readOnly = false }: TaskCardProps) {
  const [isDragging, setIsDragging] = useState(false);
  const [isLoadingGuidance, setIsLoadingGuidance] = useState(false);
  const [guidanceData, setGuidanceData] = useState<GuidanceData | null>(null);
  const loadedForTaskId = useRef<string | null>(null);

  const { register, getValues, reset } = useForm<TaskCardFormValues>({
    defaultValues: {
      title: task.title,
      acceptanceCriterion: task.acceptanceCriterion,
      storyPoints: task.storyPoints,
    },
  });

  useEffect(() => {
    reset({
      title: task.title,
      acceptanceCriterion: task.acceptanceCriterion,
      storyPoints: task.storyPoints,
    });
  }, [task, reset]);

  // Load guidance when panel opens, but only once per task
  useEffect(() => {
    if (!isGuidanceOpen || loadedForTaskId.current === task.id) return;
    loadedForTaskId.current = task.id;
    setIsLoadingGuidance(true);
    getTaskGuidance(sprintId, task.id)
      .then((data) => setGuidanceData(data))
      .catch(() => setGuidanceData({ hints: 'Could not load guidance.', suggestedApproach: '', riskFlags: [] }))
      .finally(() => setIsLoadingGuidance(false));
  }, [isGuidanceOpen, sprintId, task.id]);

  const isCarryover = task.assignmentReason?.startsWith('Carry-over');

  const commit = (field: keyof Task, value: string | number) => {
    const oldValue = task[field];
    if (oldValue === value) return;
    onEdit(task.id, field, oldValue as string | number, value);
  };

  return (
    <article
      draggable={!readOnly}
      onDragStart={(event) => {
        if (readOnly) return;
        setIsDragging(true);
        event.dataTransfer.setData('text/task-id', task.id);
      }}
      onDragEnd={() => setIsDragging(false)}
      className={`space-y-2 rounded-xl border bg-slate-950/60 p-3 transition ${
        readOnly ? 'cursor-default' : 'cursor-grab active:cursor-grabbing'
      } ${isDragging ? 'opacity-40 scale-95' : 'opacity-100'} ${
        isDeferred
          ? 'border-l-4 border-l-amber-400 border-r-slate-500/40 border-t-slate-500/40 border-b-slate-500/40'
          : 'border-slate-500/40'
      }`}
    >
      {isCarryover && (
        <span className="inline-flex items-center gap-1 rounded-full border border-amber-500/30 bg-amber-950/40 px-2 py-0.5 text-xs font-medium text-amber-300">
          ↩ Carry-over
        </span>
      )}
      <div className="flex items-start justify-between gap-2">
        <input
          className="flex-1 rounded-md border border-slate-500/40 px-2 py-1 text-sm font-semibold disabled:cursor-default"
          disabled={readOnly}
          {...register('title')}
          onBlur={() => { if (!readOnly) commit('title', getValues('title')); }}
        />
        {isDeferred && (
          <span className="mt-1 whitespace-nowrap rounded-md bg-amber-500/20 px-2 py-1 text-xs font-medium text-amber-300">
            Deferred
          </span>
        )}
      </div>
      {!isCarryover && task.assignmentReason && (
        <p className="mt-1 text-xs text-slate-500 italic">
          💡 {task.assignmentReason}
        </p>
      )}
      {!isCarryover && !task.assignedDevId && task.assignmentReason && (
        <p className="mt-1 text-xs text-amber-500/70 italic">
          ⚠️ {task.assignmentReason}
        </p>
      )}
      {isCarryover && task.assignmentReason && (
        <p className="mt-1 text-xs text-amber-400/70">{task.assignmentReason}</p>
      )}
      <textarea
        className="h-16 w-full rounded-md border border-slate-500/40 px-2 py-1 text-xs disabled:cursor-default"
        disabled={readOnly}
        {...register('acceptanceCriterion')}
        onBlur={() => { if (!readOnly) commit('acceptanceCriterion', getValues('acceptanceCriterion')); }}
      />
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <span className="rounded-md bg-slate-700/60 px-2 py-1 text-xs uppercase tracking-wide text-slate-200">{task.roleTag}</span>
          <input
            type="number"
            min={1}
            className="w-16 rounded-md border border-slate-500/40 px-2 py-1 text-xs disabled:cursor-default"
            disabled={readOnly}
            {...register('storyPoints', { valueAsNumber: true, min: 1 })}
            onBlur={() => { if (!readOnly) commit('storyPoints', getValues('storyPoints')); }}
          />
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            className={`rounded-md border px-2 py-1 text-xs transition-colors ${
              isGuidanceOpen
                ? 'border-sky-400/50 bg-sky-500/10 text-sky-300'
                : 'border-slate-500/40 text-slate-400 hover:bg-slate-800/40'
            }`}
            onClick={() => onGuidanceToggle(isGuidanceOpen ? null : task.id)}
            title="Get implementation hints"
          >
            {isLoadingGuidance ? '💡 ...' : '💡'}
          </button>
          <button
            disabled={readOnly}
            className={`rounded-md border px-2 py-1 text-xs transition-colors disabled:cursor-default disabled:opacity-50 ${
              isDeferred
                ? 'border-amber-400/50 bg-amber-500/10 text-amber-300 hover:bg-amber-500/20'
                : 'border-ink/20 hover:bg-ink/10 text-slate-300'
            }`}
            onClick={() => { if (!readOnly) onDefer(task.id); }}
          >
            {isDeferred ? 'Un-defer' : 'Defer'}
          </button>
          <HelpTooltip
            text="Deferred tasks are removed from this sprint and logged. The AI will factor this into future planning."
            position="top"
          />
        </div>
      </div>

      {/* Guidance panel */}
      {isGuidanceOpen && (
        <div className="mt-2 rounded-xl border border-sky-500/20 bg-gradient-to-b from-sky-950/50 to-slate-950/50 p-3 text-xs">
          <div className="mb-3 flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <span className="text-base">💡</span>
              <span className="font-semibold tracking-wide text-sky-200">Implementation Hints</span>
            </div>
            <button
              type="button"
              className="rounded p-0.5 text-sky-500 transition-colors hover:bg-sky-900/40 hover:text-sky-200"
              onClick={() => onGuidanceToggle(null)}
            >
              ✕
            </button>
          </div>
          {isLoadingGuidance ? (
            <div className="space-y-2">
              <div className="h-2.5 w-3/4 animate-pulse rounded bg-sky-900/60" />
              <div className="h-2.5 w-1/2 animate-pulse rounded bg-sky-900/60" />
              <div className="h-2.5 w-2/3 animate-pulse rounded bg-sky-900/60" />
            </div>
          ) : guidanceData ? (
            <div className="space-y-3">
              {guidanceData.hints && (
                <p className="leading-relaxed text-slate-200">{guidanceData.hints}</p>
              )}
              {guidanceData.suggestedApproach && (
                <div className="rounded-lg border border-sky-500/15 bg-sky-900/20 p-2.5">
                  <p className="mb-1.5 font-semibold text-sky-300">Suggested approach</p>
                  <p className="whitespace-pre-line leading-relaxed text-sky-100">{guidanceData.suggestedApproach}</p>
                </div>
              )}
              {guidanceData.riskFlags.length > 0 && (
                <div className="space-y-1.5">
                  <p className="font-semibold text-amber-300">Risk flags</p>
                  {guidanceData.riskFlags.map((flag, i) => (
                    <div key={i} className="flex items-start gap-1.5 rounded-md border border-amber-500/20 bg-amber-900/20 px-2 py-1.5 text-amber-200">
                      <span className="mt-0.5 shrink-0">⚠</span>
                      <span>{flag}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : null}
        </div>
      )}
    </article>
  );
}
