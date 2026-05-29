import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import type { CapabilityTag, Developer } from '../../types/team';
import { HelpTooltip } from '../../components/shared/HelpTooltip';

interface DeveloperFormValues {
  name: string;
  role: Developer['role'];
  capabilitiesText: string;
  baseVelocity: number;
}

interface DeveloperFormProps {
  onSubmit: (values: { name: string; role: string; capabilities: CapabilityTag[]; baseVelocity: number }) => void;
  isSubmitting: boolean;
}

const COMMON_CAPABILITIES: CapabilityTag[] = ['backend', 'frontend', 'qa', 'devops', 'android', 'ios', 'fullstack'];

function normalizeCapabilities(values: string[]): CapabilityTag[] {
  const normalized: CapabilityTag[] = [];
  for (const value of values) {
    const cleaned = value.trim().toLowerCase();
    if (cleaned && !normalized.includes(cleaned)) {
      normalized.push(cleaned);
    }
  }
  return normalized;
}

export function DeveloperForm({ onSubmit, isSubmitting }: DeveloperFormProps) {
  const { register, handleSubmit, reset } = useForm<DeveloperFormValues>({
    defaultValues: {
      name: '',
      role: 'Backend Engineer',
      capabilitiesText: 'backend',
      baseVelocity: 5,
    },
  });
  const [selectedCapabilities, setSelectedCapabilities] = useState<CapabilityTag[]>(['backend']);

  const capabilityOptions = useMemo(() => COMMON_CAPABILITIES, []);

  return (
    <form
      className="grid gap-3 rounded-2xl border border-white/10 bg-slate-900/50 p-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4"
      onSubmit={handleSubmit((values) => {
        const customCapabilities = values.capabilitiesText.split(',');
        const merged = normalizeCapabilities([...selectedCapabilities, ...customCapabilities]);
        onSubmit({
          name: values.name,
          role: values.role,
          capabilities: merged,
          baseVelocity: values.baseVelocity,
        });
        reset();
        setSelectedCapabilities(['backend']);
      })}
    >
      <input
        className="rounded-lg border border-slate-500/40 px-3 py-2 text-sm"
        placeholder="Developer name"
        {...register('name', { required: true })}
      />
      <input
        className="rounded-lg border border-slate-500/40 px-3 py-2 text-sm"
        placeholder="Role label (e.g. Senior Backend)"
        {...register('role', { required: true })}
      />
      <input
        className="rounded-lg border border-slate-500/40 px-3 py-2 text-sm"
        type="number"
        min={1}
        {...register('baseVelocity', { valueAsNumber: true, min: 1 })}
      />
      <button className="rounded-lg bg-emerald-500/85 px-4 py-2 text-sm font-semibold text-slate-950" disabled={isSubmitting}>
        {isSubmitting ? 'Adding...' : 'Add developer'}
      </button>

      <div className="sm:col-span-2 lg:col-span-4">
        <div className="flex items-center gap-1.5">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-300">Capabilities</p>
          <HelpTooltip
            text="Used for task routing. The AI only assigns backend tasks to developers with backend capability."
            position="right"
          />
        </div>
        <div className="mt-2 flex flex-wrap gap-2">
          {capabilityOptions.map((capability) => (
            <label
              key={capability}
              className={`flex items-center gap-2 rounded-full border px-3 py-1 text-xs uppercase tracking-wide ${
                selectedCapabilities.includes(capability)
                  ? 'border-emerald-300/60 bg-emerald-400/10 text-emerald-100'
                  : 'border-white/10 text-slate-300'
              }`}
            >
              <input
                type="checkbox"
                className="accent-emerald-400"
                checked={selectedCapabilities.includes(capability)}
                onChange={(event) => {
                  setSelectedCapabilities((current) => {
                    if (event.target.checked) {
                      return normalizeCapabilities([...current, capability]);
                    }
                    return current.filter((item) => item !== capability);
                  });
                }}
              />
              {capability}
            </label>
          ))}
        </div>
        <input
          className="mt-3 w-full rounded-lg border border-slate-500/40 px-3 py-2 text-sm"
          placeholder="Custom capabilities (comma-separated)"
          {...register('capabilitiesText')}
        />
        <p className="mt-1 text-xs text-slate-400">Used for task routing, e.g. backend, android, fullstack.</p>
      </div>
    </form>
  );
}
