import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import type { Task } from '../../types/sprint';

interface TaskCardProps {
  task: Task;
  onEdit: (taskId: string, field: string, oldValue: string | number, newValue: string | number) => void;
  onDefer: (taskId: string) => void;
  isDeferred: boolean;
}

interface TaskCardFormValues {
  title: string;
  acceptanceCriterion: string;
  storyPoints: number;
}

export function TaskCard({ task, onEdit, onDefer, isDeferred }: TaskCardProps) {
  const [isDragging, setIsDragging] = useState(false);
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

  const commit = (field: keyof Task, value: string | number) => {
    const oldValue = task[field];
    if (oldValue === value) {
      return;
    }
    onEdit(task.id, field, oldValue as string | number, value);
  };

  return (
    <article
      draggable
      onDragStart={(event) => {
        setIsDragging(true);
        event.dataTransfer.setData('text/task-id', task.id);
      }}
      onDragEnd={() => setIsDragging(false)}
      className={`space-y-2 rounded-xl border bg-slate-950/60 p-3 cursor-grab active:cursor-grabbing transition ${
        isDragging ? 'opacity-40 scale-95' : 'opacity-100'
      } ${
        isDeferred
          ? 'border-l-4 border-l-amber-400 border-r-slate-500/40 border-t-slate-500/40 border-b-slate-500/40'
          : 'border-slate-500/40'
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <input
          className="flex-1 rounded-md border border-slate-500/40 px-2 py-1 text-sm font-semibold"
          {...register('title')}
          onBlur={() => commit('title', getValues('title'))}
        />
        {isDeferred && (
          <span className="mt-1 whitespace-nowrap rounded-md bg-amber-500/20 px-2 py-1 text-xs font-medium text-amber-300">
            Deferred
          </span>
        )}
      </div>
      <textarea
        className="h-16 w-full rounded-md border border-slate-500/40 px-2 py-1 text-xs"
        {...register('acceptanceCriterion')}
        onBlur={() => commit('acceptanceCriterion', getValues('acceptanceCriterion'))}
      />
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <span className="rounded-md bg-slate-700/60 px-2 py-1 text-xs uppercase tracking-wide text-slate-200">{task.roleTag}</span>
          <input
            type="number"
            min={1}
            className="w-16 rounded-md border border-slate-500/40 px-2 py-1 text-xs"
            {...register('storyPoints', { valueAsNumber: true, min: 1 })}
            onBlur={() => commit('storyPoints', getValues('storyPoints'))}
          />
        </div>
        <button
          className={`rounded-md border px-2 py-1 text-xs transition-colors ${
            isDeferred
              ? 'border-amber-400/50 bg-amber-500/10 text-amber-300 hover:bg-amber-500/20'
              : 'border-ink/20 hover:bg-ink/10 text-slate-300'
          }`}
          onClick={() => onDefer(task.id)}
        >
          {isDeferred ? 'Un-defer' : 'Defer'}
        </button>
      </div>
    </article>
  );
}
