import type { Task, UserStory } from '../../types/sprint';
import { TaskCard } from './TaskCard';
import { useMemo } from 'react';

interface DeveloperLaneProps {
  developerId: string;
  developerLabel: string;
  tasks: Task[];
  capacity: number;
  onDropTask: (taskId: string, developerId: string) => void;
  onEdit: (taskId: string, field: string, oldValue: string | number, newValue: string | number) => void;
  onDefer: (taskId: string) => void;
  stories?: UserStory[];
}

export function DeveloperLane({ developerId, developerLabel, tasks, capacity, onDropTask, onEdit, onDefer, stories }: DeveloperLaneProps) {
  const points = tasks.reduce((sum, task) => sum + task.storyPoints, 0);
  const indicator = points > capacity ? 'text-red-500' : points > capacity * 0.8 ? 'text-amber-500' : 'text-green-500';
  const isUnassigned = developerId === 'unassigned';
  const fillPercent = Math.min(100, capacity > 0 ? (points / capacity) * 100 : 0);
  const barColor = points > capacity ? 'bg-red-500' : points > capacity * 0.8 ? 'bg-amber-500' : 'bg-green-500';

  return (
    <section
      className="space-y-3 rounded-2xl border border-white/10 bg-slate-950/60 p-4"
      onDragOver={(event) => event.preventDefault()}
      onDrop={(event) => {
        event.preventDefault();
        const taskId = event.dataTransfer.getData('text/task-id');
        if (taskId) {
          onDropTask(taskId, developerId);
        }
      }}
    >
      <header className="space-y-2">
        <div className="flex items-center justify-between">
          <h3 className={`text-base font-semibold ${isUnassigned ? 'text-red-400' : 'text-slate-100'}`}>{developerLabel}</h3>
          {!isUnassigned && (
            <p className={`text-xs font-bold ${indicator}`}>
              {points} / {capacity} pts
            </p>
          )}
        </div>
        {!isUnassigned && (
          <div className="h-2 w-full overflow-hidden rounded-full bg-slate-700">
            <div
              className={`h-full rounded-full transition-all ${barColor}`}
              style={{ width: `${fillPercent}%` }}
            />
          </div>
        )}
      </header>
      <div className="space-y-2">
        {useMemo(() => {
          const tasksByStory = new Map<string, Task[]>();
          tasks.forEach((task) => {
            const key = task.storyId ?? 'unassigned';
            if (!tasksByStory.has(key)) tasksByStory.set(key, []);
            tasksByStory.get(key)!.push(task);
          });

          return Array.from(tasksByStory.entries()).map(([storyId, storyTasks]) => (
            <div key={storyId}>
              <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-400">
                {(() => {
                  const story = (stories ?? []).find((s: UserStory) => s.id === storyId);
                  return story?.epicTitle ?? 'Unassigned';
                })()}
              </div>
              <div className="space-y-2">
                {storyTasks.map((task) => (
                  <TaskCard key={task.id} task={task} onEdit={onEdit} onDefer={onDefer} />
                ))}
              </div>
            </div>
          ));
        }, [tasks, stories, onEdit, onDefer])}
      </div>
    </section>
  );
}
