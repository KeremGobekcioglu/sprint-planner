import { useState } from 'react';
import type { Epic } from '../../types/planning';

interface EpicInputProps {
  epics: Epic[];
  onChange: (epics: Epic[]) => void;
}

export function parsePasteText(text: string): Epic[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line): Epic => {
      // Strip leading [PROJ-XXX] patterns and numbered list prefixes
      let cleaned = line
        .replace(/^\s*\[[^\]]*\]\s*/, '')
        .replace(/^\s*\d+\.\s*/, '')
        .trim();

      // Split on first |
      const pipeIdx = cleaned.indexOf('|');
      if (pipeIdx !== -1) {
        return {
          title: cleaned.slice(0, pipeIdx).trim(),
          description: cleaned.slice(pipeIdx + 1).trim(),
        };
      }

      // Split on first " - " (dash with surrounding spaces)
      const dashMatch = cleaned.match(/^(.+?) - (.+)$/);
      if (dashMatch) {
        return { title: dashMatch[1]!.trim(), description: dashMatch[2]!.trim() };
      }

      // Split on first ": " (colon space)
      const colonIdx = cleaned.indexOf(': ');
      if (colonIdx !== -1) {
        return {
          title: cleaned.slice(0, colonIdx).trim(),
          description: cleaned.slice(colonIdx + 2).trim(),
        };
      }

      return { title: cleaned, description: '' };
    })
    .filter((epic) => epic.title.length > 0);
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
        <div className="space-y-1">
          <textarea
            className="h-44 w-full resize-y rounded-xl border border-slate-500/40 bg-slate-950/60 px-3 py-2 text-sm text-slate-200 placeholder-slate-600"
            placeholder={`One epic per line. Supported formats:\nCheckout Flow | Implement checkout with validation\nPayment Integration\n3. Search Autosuggest - Add autosuggest for search\n[PROJ-123] Inventory Sync\nInventory Sync: Background sync with warehouse`}
            value={pasteText}
            onChange={(e) => setPasteText(e.target.value)}
          />
          <p className="text-xs text-slate-500">Switch to Cards to see parsed results.</p>
        </div>
      )}
    </div>
  );
}
