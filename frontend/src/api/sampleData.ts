import type { Developer, Team } from '../types/team';
import type { Epic } from '../types/planning';

/**
 * Sample data for rapid testing and demo purposes.
 * Not persisted to the backend - all changes are ephemeral (localStorage only).
 */

export const SAMPLE_TEAMS: Team[] = [
  {
    id: 'sample-team-1',
    name: 'PayFlow Team',
    domain: 'FINTECH',
    sprintLengthDays: 10,
    trelloBoardId: 'payflow-board',
    createdAt: new Date().toISOString(),
  },
  {
    id: 'sample-team-2',
    name: 'ShopHub Team',
    domain: 'E_COMMERCE',
    sprintLengthDays: 14,
    trelloBoardId: 'shophub-board',
    createdAt: new Date().toISOString(),
  },
];

export const SAMPLE_DEVELOPERS: Developer[] = [
  // PayFlow Team - Fintech
  {
    id: 'sample-dev-1',
    teamId: 'sample-team-1',
    name: 'Alex Chen',
    role: 'Senior Backend Engineer',
    capabilities: ['backend'],
    baseVelocity: 13,
  },
  {
    id: 'sample-dev-2',
    teamId: 'sample-team-1',
    name: 'Jordan López',
    role: 'Backend Engineer',
    capabilities: ['backend'],
    baseVelocity: 8,
  },
  {
    id: 'sample-dev-3',
    teamId: 'sample-team-1',
    name: 'Maya Patel',
    role: 'Frontend Engineer',
    capabilities: ['frontend'],
    baseVelocity: 7,
  },
  {
    id: 'sample-dev-4',
    teamId: 'sample-team-1',
    name: 'Sam Wilson',
    role: 'QA Engineer',
    capabilities: ['qa'],
    baseVelocity: 5,
  },
  {
    id: 'sample-dev-5',
    teamId: 'sample-team-1',
    name: 'Casey Kim',
    role: 'DevOps Engineer',
    capabilities: ['devops'],
    baseVelocity: 6,
  },

  // ShopHub Team - E-Commerce
  {
    id: 'sample-dev-6',
    teamId: 'sample-team-2',
    name: 'Ava Rodriguez',
    role: 'Frontend Lead',
    capabilities: ['frontend'],
    baseVelocity: 9,
  },
  {
    id: 'sample-dev-7',
    teamId: 'sample-team-2',
    name: 'Marcus Thompson',
    role: 'Backend Engineer',
    capabilities: ['backend'],
    baseVelocity: 8,
  },
  {
    id: 'sample-dev-8',
    teamId: 'sample-team-2',
    name: 'Priya Singh',
    role: 'Senior Backend Engineer',
    capabilities: ['backend'],
    baseVelocity: 11,
  },
  {
    id: 'sample-dev-9',
    teamId: 'sample-team-2',
    name: 'Liam OReilley',
    role: 'QA Lead',
    capabilities: ['qa'],
    baseVelocity: 7,
  },
  {
    id: 'sample-dev-10',
    teamId: 'sample-team-2',
    name: 'Sophie Bergström',
    role: 'Frontend Engineer',
    capabilities: ['frontend'],
    baseVelocity: 6,
  },
];

// Fintech epics
export const SAMPLE_EPICS_PAYFLOW: Epic[] = [
  {
    title: 'Multi-Currency Payment Processing',
    description: 'Implement support for processing payments in 50+ currencies with real-time exchange rates and compliance checks',
  },
  {
    title: 'Advanced Fraud Detection System',
    description: 'Build ML-based fraud detection with machine learning models to identify suspicious transactions and prevent chargebacks',
  },
  {
    title: 'Settlement & Reconciliation Dashboard',
    description: 'Create a comprehensive dashboard for tracking settlements, reconciling payments, and generating audit reports',
  },
  {
    title: 'Webhook Infrastructure & Event System',
    description: 'Design and implement a robust webhook system for real-time transaction notifications and integration callbacks',
  },
  {
    title: 'Regulatory Compliance Module',
    description: 'Implement KYC/AML compliance checks, PCI DSS enforcement, and audit logging for regulatory requirements',
  },
];

// E-Commerce epics
export const SAMPLE_EPICS_SHOPHUB: Epic[] = [
  {
    title: 'Dynamic Inventory Management System',
    description: 'Build real-time inventory tracking with multi-warehouse support, low stock alerts, and automated reorder workflows',
  },
  {
    title: 'AI-Powered Product Recommendations',
    description: 'Implement machine learning engine for personalized product recommendations based on browsing history and purchase behavior',
  },
  {
    title: 'Cart Optimization & Checkout Flow Redesign',
    description: 'Refactor checkout process with cart abandonment recovery, one-click purchasing, and Apple Pay/Google Pay integration',
  },
  {
    title: 'Customer Review & Rating Platform',
    description: 'Launch comprehensive review system with verified purchase badges, moderation tools, and integration with product pages',
  },
  {
    title: 'Marketing Analytics & Campaign Management',
    description: 'Create analytics dashboard for campaign tracking, A/B testing, email campaign management, and ROI calculations',
  },
];

export const SAMPLE_SPRINT_GOALS = {
  'sample-team-1': 'Launch multi-currency support and strengthen fraud detection for market expansion',
  'sample-team-2': 'Improve conversion rates and enhance post-purchase customer experience',
};

export function getSampleEpicsForTeam(teamId: string): Epic[] {
  if (teamId === 'sample-team-1') {
    return SAMPLE_EPICS_PAYFLOW;
  }
  if (teamId === 'sample-team-2') {
    return SAMPLE_EPICS_SHOPHUB;
  }
  return [];
}

export function getSampleSprintGoal(teamId: string): string {
  return SAMPLE_SPRINT_GOALS[teamId as keyof typeof SAMPLE_SPRINT_GOALS] || 'Complete planned features on schedule';
}

export function getSampleTeam(teamId: string): Team | undefined {
  return SAMPLE_TEAMS.find((team) => team.id === teamId);
}

export function getSampleDevelopers(teamId: string): Developer[] {
  return SAMPLE_DEVELOPERS.filter((developer) => developer.teamId === teamId);
}

export function getSamplePayloadForTeam(teamId: string) {
  const team = getSampleTeam(teamId);
  if (!team) {
    return null;
  }

  return {
    team,
    developers: getSampleDevelopers(teamId),
    epics: getSampleEpicsForTeam(teamId),
    sprintGoal: getSampleSprintGoal(teamId),
  };
}
