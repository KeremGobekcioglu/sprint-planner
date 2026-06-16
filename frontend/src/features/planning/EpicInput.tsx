import { useMemo, useState } from 'react';
import type { Epic } from '../../types/planning';

interface EpicInputProps {
  epics: Epic[];
  onChange: (epics: Epic[]) => void;
}

function parseEpicLine(line: string): Epic | null {
  const trimmed = line.trim();
  if (!trimmed) return null;

  // Strip leading number prefix (1. or 1) or Jira prefix ([PROJ-123])
  const withoutPrefix = trimmed
    .replace(/^\d+\.\s*/, '')
    .replace(/^\[\w+-\d+\]\s*/, '');

  // Try delimiters in order of specificity
  const pipeMatch = withoutPrefix.match(/^(.+?)\s*\|\s*(.+)$/);
  if (pipeMatch) return { title: pipeMatch[1]!.trim(), description: pipeMatch[2]!.trim() };

  const colonMatch = withoutPrefix.match(/^(.+?)\s*:\s*(.+)$/);
  if (colonMatch) return { title: colonMatch[1]!.trim(), description: colonMatch[2]!.trim() };

  const dashMatch = withoutPrefix.match(/^(.+?)\s+-\s+(.+)$/);
  if (dashMatch) return { title: dashMatch[1]!.trim(), description: dashMatch[2]!.trim() };

  // No delimiter — treat whole line as title
  return { title: withoutPrefix.trim(), description: '' };
}

export function parsePasteText(text: string): Epic[] {
  return text
    .split('\n')
    .map(parseEpicLine)
    .filter((e): e is Epic => e !== null && e.title.length > 0);
}

function serializeEpicsToText(epics: Epic[]): string {
  return epics
    .filter((e) => e.title.trim())
    .map((e) => (e.description.trim() ? `${e.title} | ${e.description}` : e.title))
    .join('\n');
}

export function EpicInput({ epics, onChange }: EpicInputProps) {
  const [mode, setMode] = useState<'cards' | 'paste'>('cards');
  const [pasteText, setPasteText] = useState('');
  const parsedPreview = useMemo(() => parsePasteText(pasteText), [pasteText]);

  function switchToCards() {
    const parsed = parsePasteText(pasteText);
    onChange(parsed.length > 0 ? parsed : [{ title: '', description: '' }]);
    setMode('cards');
  }

  function switchToPaste() {
    setPasteText(serializeEpicsToText(epics));
    setMode('paste');
  }

  function updateEpic(index: number, field: 'title' | 'description', value: string) {
    onChange(epics.map((e, i) => (i === index ? { ...e, [field]: value } : e)));
  }

  function removeEpic(index: number) {
    const next = epics.filter((_e, i) => i !== index);
    onChange(next.length > 0 ? next : [{ title: '', description: '' }]);
  }

  function addEpic() {
    onChange([...epics, { title: '', description: '' }]);
  }

  return (
    <div className="space-y-3">
      {/* Mode toggle */}
      <div className="flex w-fit gap-1 rounded-lg border border-slate-600/40 p-1">
        <button
          type="button"
          onClick={() => { if (mode === 'paste') switchToCards(); }}
          className={`rounded-md px-3 py-1 text-xs font-medium transition-colors ${
            mode === 'cards' ? 'bg-slate-700 text-slate-100' : 'text-slate-400 hover:text-slate-300'
          }`}
        >
          📋 Cards
        </button>
        <button
          type="button"
          onClick={() => { if (mode === 'cards') switchToPaste(); }}
          className={`rounded-md px-3 py-1 text-xs font-medium transition-colors ${
            mode === 'paste' ? 'bg-slate-700 text-slate-100' : 'text-slate-400 hover:text-slate-300'
          }`}
        >
          📄 Paste
        </button>
      </div>

      {mode === 'cards' ? (
        <div className="space-y-2">
          {epics.length === 0 ? (
            <p className="py-3 text-sm text-slate-500">
              No epics yet. Click &quot;+ Add epic&quot; to start, or switch to Paste mode to bulk import.
            </p>
          ) : (
            epics.map((epic, index) => (
              <div
                key={index}
                className="overflow-hidden rounded-xl border border-slate-600/40 bg-slate-950/40"
              >
                <div className="flex items-center gap-2 px-3 pt-2.5">
                  <input
                    type="text"
                    className="flex-1 bg-transparent text-sm font-medium text-slate-100 placeholder-slate-500 outline-none"
                    placeholder="Epic title (e.g. Checkout Flow)"
                    value={epic.title}
                    onChange={(e) => updateEpic(index, 'title', e.target.value)}
                  />
                  {epics.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeEpic(index)}
                      className="px-1 text-sm leading-none text-slate-500 transition-colors hover:text-red-400"
                      aria-label="Remove epic"
                    >
                      ✕
                    </button>
                  )}
                </div>
                <div className="mt-2 border-t border-slate-700/50 px-3 pb-2.5 pt-1.5">
                  <input
                    type="text"
                    className="w-full bg-transparent text-sm text-slate-400 placeholder-slate-600 outline-none"
                    placeholder="What needs to be built (optional)"
                    value={epic.description}
                    onChange={(e) => updateEpic(index, 'description', e.target.value)}
                  />
                </div>
              </div>
            ))
          )}
          <button
            type="button"
            onClick={addEpic}
            className="text-sm text-slate-400 transition-colors hover:text-slate-200"
          >
            + Add epic
          </button>
        </div>
      ) : (
        <div className="space-y-2">
          <textarea
            className="h-44 w-full resize-y rounded-xl border border-slate-500/40 bg-slate-950/60 px-3 py-2 text-sm text-slate-200 placeholder-slate-600"
            placeholder={`One epic per line. Supported formats:\nCheckout Flow | Implement checkout with validation\nPayment Integration\n3. Search Autosuggest - Add autosuggest for search\n[PROJ-123] Inventory Sync\nInventory Sync: Background sync with warehouse`}
            value={pasteText}
            onChange={(e) => setPasteText(e.target.value)}
          />
          {pasteText.trim() && (
            <div className="rounded-lg border border-slate-600/40 bg-slate-950/40 px-3 py-2.5">
              <p className="mb-1.5 text-xs font-semibold uppercase tracking-widest text-slate-500">
                Preview — {parsedPreview.length} epic{parsedPreview.length !== 1 ? 's' : ''} parsed
              </p>
              {parsedPreview.length === 0 ? (
                <p className="text-xs text-amber-400">No epics recognised — check your format.</p>
              ) : (
                <ul className="space-y-1">
                  {parsedPreview.map((epic, i) => (
                    <li key={i} className="text-xs">
                      <span className="font-medium text-slate-200">{epic.title}</span>
                      {epic.description && (
                        <span className="text-slate-500"> — {epic.description}</span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
