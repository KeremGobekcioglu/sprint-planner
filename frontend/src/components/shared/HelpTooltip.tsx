import { useState } from 'react';

interface HelpTooltipProps {
  text: string;
  position?: 'top' | 'bottom' | 'left' | 'right';
}

const POSITION_CLASS: Record<NonNullable<HelpTooltipProps['position']>, string> = {
  top: 'bottom-full left-1/2 -translate-x-1/2 mb-2',
  bottom: 'top-full left-1/2 -translate-x-1/2 mt-2',
  left: 'right-full top-1/2 -translate-y-1/2 mr-2',
  right: 'left-full top-1/2 -translate-y-1/2 ml-2',
};

export function HelpTooltip({ text, position = 'top' }: HelpTooltipProps) {
  const [visible, setVisible] = useState(false);

  return (
    <span className="relative inline-flex shrink-0 items-center">
      <button
        type="button"
        onMouseEnter={() => setVisible(true)}
        onMouseLeave={() => setVisible(false)}
        onFocus={() => setVisible(true)}
        onBlur={() => setVisible(false)}
        className="flex h-4 w-4 items-center justify-center rounded-full border border-slate-600 bg-slate-800 text-[10px] font-bold text-slate-400 hover:border-slate-400 hover:text-slate-200 focus:outline-none"
        aria-label="Help"
      >
        ?
      </button>
      {visible && (
        <span
          role="tooltip"
          className={`pointer-events-none absolute z-50 w-56 rounded-lg bg-slate-700 px-3 py-2 text-xs leading-relaxed text-slate-100 shadow-xl ${POSITION_CLASS[position]}`}
        >
          {text}
        </span>
      )}
    </span>
  );
}
