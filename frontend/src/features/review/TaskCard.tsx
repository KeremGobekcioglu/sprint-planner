import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import type { Task } from '../../types/sprint';

interface TaskCardProps {
  task: Task;
  onEdit: (taskId: string, field: string, oldValue: string | number, newValue: string | number) => void;
  onDefer: (taskId: string) => void;
}

interface TaskCardFormValues {
  title: string;
  acceptanceCriterion: string;
  storyPoints: number;
}

export function TaskCard({ task, onEdit, onDefer }: TaskCardProps) {
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
        event.dataTransfer.setData('text/task-id', task.id);
      }}
      className="space-y-2 rounded-xl border border-slate-500/40 bg-slate-950/60 p-3"
    >
      <input
        className="w-full rounded-md border border-slate-500/40 px-2 py-1 text-sm font-semibold"
        {...register('title')}
        onBlur={() => commit('title', getValues('title'))}
      />
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
        <button className="rounded-md border border-ink/20 px-2 py-1 text-xs" onClick={() => onDefer(task.id)}>
          Defer
        </button>
      </div>
    </article>
  );
}
