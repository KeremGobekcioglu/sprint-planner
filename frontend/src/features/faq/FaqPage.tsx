import { useState } from 'react';
import { Link } from 'react-router-dom';

type AnswerBlock =
  | { type: 'paragraph'; text: string }
  | { type: 'list'; items: string[] };

interface FaqEntry {
  question: string;
  content: AnswerBlock[];
}

const FAQ_ITEMS: FaqEntry[] = [
  {
    question: 'How does the AI plan my sprint?',
    content: [
      { type: 'paragraph', text: 'The system uses four specialized AI agents working in sequence:' },
      {
        type: 'list',
        items: [
          'Breakdown Agent — reads your epics and breaks them into user stories and tasks with acceptance criteria',
          'Estimation Agent — assigns story points to each task using Fibonacci scale (1, 2, 3, 5, 8, 13)',
          'Capacity Agent — calculates how many story points each developer can handle based on velocity and days off',
          'Distribution Agent — assigns tasks to developers matching their capabilities and respecting capacity limits',
        ],
      },
      {
        type: 'paragraph',
        text: 'Tasks that can\'t be assigned (no capable developer, or all capacity used) appear as "Unassigned" and you can drag them to a developer manually.',
      },
    ],
  },
  {
    question: 'What are epics?',
    content: [
      {
        type: 'paragraph',
        text: 'An epic is a large feature or piece of work. Examples: "Add checkout flow", "User authentication", "Analytics dashboard".',
      },
      {
        type: 'paragraph',
        text: 'You can add a description after a | separator: "Add checkout flow | Redesign the 3-step checkout as a single page".',
      },
      { type: 'paragraph', text: 'The AI breaks each epic into 1–3 user stories, each with 2–4 tasks.' },
    ],
  },
  {
    question: 'What are story points?',
    content: [
      {
        type: 'paragraph',
        text: 'Story points are a relative measure of effort, not time. We use the Fibonacci scale: 1 (trivial), 2 (easy), 3 (medium), 5 (hard), 8 (very hard), 13 (very complex).',
      },
      { type: 'paragraph', text: "The AI estimates points based on task complexity and your team's history." },
    ],
  },
  {
    question: 'What is base velocity?',
    content: [
      {
        type: 'paragraph',
        text: 'Base velocity is how many story points a developer can realistically complete in one full sprint. It is used to calculate their capacity. A developer with velocity 8 on a 10-day sprint gets 8 points of work assigned (or less if they have days off).',
      },
    ],
  },
  {
    question: 'What is Fresh Start vs Continuous mode?',
    content: [
      {
        type: 'list',
        items: [
          'Fresh Start: first sprint with this team. The AI uses conservative defaults.',
          'Continuous: you have completed at least one sprint review. The AI uses your actual completion history and preferences learned from your edits.',
        ],
      },
    ],
  },
  {
    question: 'How does the AI learn from my edits?',
    content: [
      {
        type: 'paragraph',
        text: 'When you edit the sprint plan on the Review screen (reassign a task, change story points, defer work), these edits are logged. After you approve the plan, the system generates a "Team Preferences" file from your edit patterns. This file is included in future AI prompts so the AI applies your preferences automatically.',
      },
    ],
  },
  {
    question: 'What does "Defer" do?',
    content: [
      {
        type: 'paragraph',
        text: 'Deferred tasks are removed from the current sprint. The edit is logged, which tells the AI that scope was reduced. Deferred tasks appear in the Unassigned lane and are not pushed to Trello.',
      },
    ],
  },
  {
    question: 'How does Trello integration work?',
    content: [
      {
        type: 'paragraph',
        text: 'Connect your Trello account on the Profile page. Link a Trello board to your team in the Team Setup page. When you approve and push a sprint, the system creates one Trello list per epic and one card per task, with the full task description and acceptance criteria in the card body.',
      },
    ],
  },
  {
    question: 'Can I import previous sprint data?',
    content: [
      {
        type: 'paragraph',
        text: 'Yes. On the Planning page in Fresh Start mode, there is an "Import Previous Sprint History" section. Paste a summary of your past sprints or upload a .txt or .md file. The AI uses this as one-time context to calibrate estimates — it does not permanently affect your history.',
      },
    ],
  },
  {
    question: 'What happens when I close a sprint?',
    content: [
      {
        type: 'paragraph',
        text: 'You mark each task as done or not done. The system records how many story points were completed vs planned per developer. This data is used to update the sprint history memory file, which improves future planning accuracy.',
      },
    ],
  },
];

interface FaqItemProps {
  entry: FaqEntry;
  open: boolean;
  onToggle: () => void;
}

function FaqItem({ entry, open, onToggle }: FaqItemProps) {
  return (
    <div className="rounded-2xl border border-white/10 bg-slate-900/50 overflow-hidden">
      <button
        type="button"
        className="flex w-full items-center justify-between px-5 py-4 text-left"
        onClick={onToggle}
        aria-expanded={open}
      >
        <span className="pr-4 font-semibold text-slate-100">{entry.question}</span>
        <span
          className={`shrink-0 text-slate-400 transition-transform duration-300 ${open ? 'rotate-180' : 'rotate-0'}`}
        >
          ▼
        </span>
      </button>
      <div
        style={{
          maxHeight: open ? '600px' : '0',
          overflow: 'hidden',
          transition: 'max-height 0.35s ease-in-out',
        }}
      >
        <div className="border-t border-white/10 px-5 pb-5 pt-4">
          <div className="space-y-3 text-sm text-slate-300">
            {entry.content.map((block, i) => {
              if (block.type === 'paragraph') {
                return <p key={i}>{block.text}</p>;
              }
              return (
                <ul key={i} className="space-y-1.5 pl-4">
                  {block.items.map((item, j) => {
                    const [bold, ...rest] = item.split(' — ');
                    return (
                      <li key={j} className="flex gap-1.5">
                        <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-400" />
                        <span>
                          {rest.length > 0 ? (
                            <>
                              <strong className="text-slate-100">{bold}</strong>
                              {' — '}
                              {rest.join(' — ')}
                            </>
                          ) : (
                            item
                          )}
                        </span>
                      </li>
                    );
                  })}
                </ul>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}

export function FaqPage() {
  const [openIndex, setOpenIndex] = useState<number | null>(0);

  return (
    <div className="min-h-screen bg-slate-950 px-4 py-12">
      <div className="mx-auto max-w-2xl">
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-slate-100">FAQ</h1>
            <p className="mt-1 text-slate-400">Frequently asked questions about Sprint Planner</p>
          </div>
          <Link
            to="/login"
            className="rounded-lg border border-slate-600 px-4 py-2 text-sm font-medium text-slate-300 hover:border-slate-400 hover:text-slate-100 transition-colors"
          >
            Sign in →
          </Link>
        </div>

        <div className="space-y-3">
          {FAQ_ITEMS.map((item, i) => (
            <FaqItem
              key={i}
              entry={item}
              open={openIndex === i}
              onToggle={() => setOpenIndex(openIndex === i ? null : i)}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
