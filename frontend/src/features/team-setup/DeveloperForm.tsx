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
  onSubmit: (values: { name: string; role: string; capabilities: CapabilityTag[]; baseVelocity: number; seniority: string }) => void;
  isSubmitting: boolean;
}

const SENIORITY_OPTIONS = ['JUNIOR', 'MID', 'SENIOR'] as const;

const COMMON_CAPABILITIES: CapabilityTag[] = ['backend', 'frontend', 'qa', 'devops', 'android', 'ios', 'fullstack', 'ai'];

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
      capabilitiesText: '',
      baseVelocity: 5,
    },
  });
  const [selectedCapabilities, setSelectedCapabilities] = useState<CapabilityTag[]>(['backend']);
  const [seniority, setSeniority] = useState<string>('MID');

  const capabilityOptions = useMemo(() => COMMON_CAPABILITIES, []);

  return (
    <form
      className="grid gap-4 rounded-2xl border border-white/10 bg-slate-900/50 p-4"
      onSubmit={handleSubmit((values) => {
        const customCapabilities = values.capabilitiesText.split(',');
        const merged = normalizeCapabilities([...selectedCapabilities, ...customCapabilities]);
        onSubmit({
          name: values.name,
          role: values.role,
          capabilities: merged,
          baseVelocity: values.baseVelocity,
          seniority,
        });
        reset();
        setSelectedCapabilities(['backend']);
        setSeniority('MID');
      })}
    >
      {/* Name */}
      <input
        className="rounded-lg border border-slate-500/40 px-3 py-2 text-sm"
        placeholder="Developer name"
        {...register('name', { required: true })}
      />

      {/* Capabilities — above job title, used by AI */}
      <div>
        <div className="flex items-center gap-1.5">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-300">What can they work on?</p>
          <HelpTooltip
            text="Used for task routing. The AI only assigns backend tasks to developers with backend capability."
            position="right"
          />
        </div>
        <p className="mt-0.5 text-xs text-slate-400">The AI uses these to route tasks. Select all that apply.</p>
        <div className="mt-2 flex flex-wrap gap-2">
          {capabilityOptions.map((capability) => (
            <label
              key={capability}
              className={`flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1 text-xs uppercase tracking-wide ${
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
        {/* capabilitiesText kept for submission logic — input not rendered */}
        <input type="hidden" {...register('capabilitiesText')} />
      </div>

      {/* Seniority */}
      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-300">Seniority level</p>
        <p className="mt-0.5 text-xs text-slate-400">Used to match complex tasks to senior developers.</p>
        <div className="mt-2 flex gap-2">
          {SENIORITY_OPTIONS.map((level) => (
            <button
              key={level}
              type="button"
              className={`rounded-full border px-3 py-1 text-xs uppercase tracking-wide ${
                seniority === level
                  ? 'border-violet-400/60 bg-violet-400/10 text-violet-100'
                  : 'border-white/10 text-slate-300'
              }`}
              onClick={() => setSeniority(level)}
            >
              {level}
            </button>
          ))}
        </div>
      </div>

      {/* Job title + Velocity — side by side */}
      <div className="grid gap-3 sm:grid-cols-2">
        <div>
          <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-300">Job title</p>
          <input
            className="w-full rounded-lg border border-slate-500/40 px-3 py-2 text-sm"
            placeholder="e.g. Senior Backend Engineer"
            {...register('role', { required: true })}
          />
          <p className="mt-1 text-xs text-slate-400">Displayed on their card. Can be anything.</p>
        </div>
        <div>
          <div className="mb-1 flex items-center gap-1.5">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-300">Sprint velocity (story points)</p>
            <HelpTooltip
              text="Story points this developer can complete in one sprint. Used to limit how much work they're assigned. Typical range: 3–13."
              position="left"
            />
          </div>
          <input
            className="w-full rounded-lg border border-slate-500/40 px-3 py-2 text-sm"
            type="number"
            min={1}
            {...register('baseVelocity', { valueAsNumber: true, min: 1 })}
          />
          <p className="mt-1 text-xs text-slate-400">How many story points this developer completes per sprint.</p>
        </div>
      </div>

      {/* Submit */}
      <button
        className="rounded-lg bg-emerald-500/85 px-4 py-2 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
        disabled={isSubmitting}
      >
        {isSubmitting ? 'Adding...' : 'Add developer'}
      </button>
    </form>
  );
}
