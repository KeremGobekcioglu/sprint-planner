import type { TeamDomain } from '../../types/team';

interface DomainSelectorProps {
  value: TeamDomain;
  onChange: (value: TeamDomain) => void;
}

export function DomainSelector({ value, onChange }: DomainSelectorProps) {
  return (
    <div className="grid grid-cols-3 gap-2">
      {[
        { key: 'E_COMMERCE', label: 'E-Commerce' },
        { key: 'FINTECH', label: 'Fintech' },
        { key: 'GENERIC', label: 'Generic' },
      ].map((domain) => (
        <button
          key={domain.key}
          type="button"
          className={`rounded-lg border px-3 py-2 text-sm font-medium ${
            value === domain.key ? 'border-emerald-400/60 bg-emerald-400/15 text-emerald-200' : 'border-slate-500/40 bg-slate-950/60 text-slate-300'
          }`}
          onClick={() => onChange(domain.key as TeamDomain)}
        >
          {domain.label}
        </button>
      ))}
    </div>
  );
}
