import type { Task } from '../../types/sprint';
import { TaskCard } from './TaskCard';

interface DeveloperLaneProps {
  developerId: string;
  tasks: Task[];
  capacity: number;
  onDropTask: (taskId: string, developerId: string) => void;
  onEdit: (taskId: string, field: string, oldValue: string | number, newValue: string | number) => void;
  onDefer: (taskId: string) => void;
}

export function DeveloperLane({ developerId, tasks, capacity, onDropTask, onEdit, onDefer }: DeveloperLaneProps) {
  const points = tasks.reduce((sum, task) => sum + task.storyPoints, 0);
  const indicator = points > capacity ? 'text-red-700' : points > capacity * 0.8 ? 'text-amber-700' : 'text-pine';

  return (
    <section
      className="space-y-3 rounded-2xl border border-ink/10 bg-white/70 p-4"
      onDragOver={(event) => event.preventDefault()}
      onDrop={(event) => {
        event.preventDefault();
        const taskId = event.dataTransfer.getData('text/task-id');
        if (taskId) {
          onDropTask(taskId, developerId);
        }
      }}
    >
      <header className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-ink">{developerId}</h3>
        <p className={`text-xs font-bold ${indicator}`}>
          {points}/{capacity} pts
        </p>
      </header>
      <div className="space-y-2">
        {tasks.map((task) => (
          <TaskCard key={task.id} task={task} onEdit={onEdit} onDefer={onDefer} />
        ))}
      </div>
    </section>
  );
}
