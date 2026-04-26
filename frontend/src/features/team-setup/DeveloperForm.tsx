import { useForm } from 'react-hook-form';
import type { Developer } from '../../types/team';

interface DeveloperFormValues {
  name: string;
  role: Developer['role'];
  baseVelocity: number;
}

interface DeveloperFormProps {
  onSubmit: (values: DeveloperFormValues) => void;
  isSubmitting: boolean;
}

export function DeveloperForm({ onSubmit, isSubmitting }: DeveloperFormProps) {
  const { register, handleSubmit, reset } = useForm<DeveloperFormValues>({
    defaultValues: {
      name: '',
      role: 'backend',
      baseVelocity: 5,
    },
  });

  return (
    <form
      className="grid gap-3 rounded-2xl border border-ink/10 bg-white p-4 sm:grid-cols-4"
      onSubmit={handleSubmit((values) => {
        onSubmit(values);
        reset();
      })}
    >
      <input
        className="rounded-lg border border-ink/20 px-3 py-2 text-sm"
        placeholder="Developer name"
        {...register('name', { required: true })}
      />
      <select className="rounded-lg border border-ink/20 px-3 py-2 text-sm" {...register('role', { required: true })}>
        <option value="backend">Backend</option>
        <option value="frontend">Frontend</option>
        <option value="qa">QA</option>
        <option value="devops">DevOps</option>
      </select>
      <input
        className="rounded-lg border border-ink/20 px-3 py-2 text-sm"
        type="number"
        min={1}
        {...register('baseVelocity', { valueAsNumber: true, min: 1 })}
      />
      <button className="rounded-lg bg-pine px-4 py-2 text-sm font-semibold text-white" disabled={isSubmitting}>
        {isSubmitting ? 'Adding...' : 'Add developer'}
      </button>
    </form>
  );
}
