import api from './axios';
import type { Developer, Team } from '../types/team';
import { SAMPLE_DEVELOPERS, SAMPLE_TEAMS } from './sampleData';

export interface TeamCreateRequest {
  name: string;
  domain: Team['domain'];
  sprintLengthDays: number;
  trelloBoardId?: string;
}

export interface DeveloperCreateRequest {
  name: string;
  role: Developer['role'];
  capabilities: Developer['capabilities'];
  baseVelocity: number;
}

const USE_REAL_API = import.meta.env.VITE_USE_REAL_API === 'true';
const MOCK_TEAMS_STORAGE_KEY = 'mock-teams';
const MOCK_DEVELOPERS_STORAGE_KEY = 'mock-developers';

const seededTeams: Team[] = [
  {
    id: 'team-atlas',
    name: 'Atlas Commerce',
    domain: 'E_COMMERCE',
    sprintLengthDays: 14,
    trelloBoardId: 'atlas-commerce-board',
    createdAt: '2026-04-01T09:00:00.000Z',
  },
  {
    id: 'team-orbit',
    name: 'Orbit Ledger',
    domain: 'FINTECH',
    sprintLengthDays: 10,
    trelloBoardId: 'orbit-ledger-board',
    createdAt: '2026-04-08T09:00:00.000Z',
  },
  {
    id: 'team-pulse',
    name: 'Pulse Health',
    domain: 'GENERIC',
    sprintLengthDays: 14,
    trelloBoardId: 'pulse-health-board',
    createdAt: '2026-04-10T11:30:00.000Z',
  },
  {
    id: 'team-shield',
    name: 'Cyber Shield',
    domain: 'FINTECH',
    sprintLengthDays: 7,
    trelloBoardId: 'shield-security-board',
    createdAt: '2026-04-15T08:15:00.000Z',
  }
];

const seededDevelopers: Developer[] = [
  // Team Atlas (E-Commerce)
  { id: 'dev-atlas-1', teamId: 'team-atlas', name: 'Ava Chen', role: 'Senior Backend', capabilities: ['backend'], baseVelocity: 8 },
  { id: 'dev-atlas-2', teamId: 'team-atlas', name: 'Marco Silva', role: 'Frontend Engineer', capabilities: ['frontend'], baseVelocity: 5 },
  { id: 'dev-atlas-3', teamId: 'team-atlas', name: 'Priya Nair', role: 'QA Engineer', capabilities: ['qa'], baseVelocity: 3 },
  { id: 'dev-atlas-4', teamId: 'team-atlas', name: 'Liam O’Connor', role: 'Backend Engineer', capabilities: ['backend'], baseVelocity: 7 },

  // Team Orbit (Fintech)
  { id: 'dev-orbit-1', teamId: 'team-orbit', name: 'Jordan Reed', role: 'Backend Engineer', capabilities: ['backend'], baseVelocity: 7 },
  { id: 'dev-orbit-2', teamId: 'team-orbit', name: 'Mina Hassan', role: 'DevOps', capabilities: ['devops'], baseVelocity: 6 },
  { id: 'dev-orbit-3', teamId: 'team-orbit', name: 'Satoshi Nakamoto', role: 'Senior Backend', capabilities: ['backend'], baseVelocity: 13 },

  // Team Pulse (Healthcare)
  { id: 'dev-pulse-1', teamId: 'team-pulse', name: 'Elena Rodriguez', role: 'Frontend Engineer', capabilities: ['frontend'], baseVelocity: 6 },
  { id: 'dev-pulse-2', teamId: 'team-pulse', name: 'Kofi Mensah', role: 'Backend Engineer', capabilities: ['backend'], baseVelocity: 8 },
  { id: 'dev-pulse-3', teamId: 'team-pulse', name: 'Sarah Jenkins', role: 'QA Engineer', capabilities: ['qa'], baseVelocity: 5 },

  // Team Shield (Security)
  { id: 'dev-shield-1', teamId: 'team-shield', name: 'Alex Volkov', role: 'Backend Engineer', capabilities: ['backend'], baseVelocity: 9 },
  { id: 'dev-shield-2', teamId: 'team-shield', name: 'Dana Scully', role: 'QA Engineer', capabilities: ['qa'], baseVelocity: 4 },
  { id: 'dev-shield-3', teamId: 'team-shield', name: 'Fox Mulder', role: 'DevOps', capabilities: ['devops'], baseVelocity: 7 }
];

function isBrowser() {
  return typeof window !== 'undefined';
}

function readMockCollection<T>(storageKey: string, fallback: T[]) {
  if (!isBrowser()) {
    return [...fallback];
  }

  const raw = window.localStorage.getItem(storageKey);
  if (!raw) {
    window.localStorage.setItem(storageKey, JSON.stringify(fallback));
    return [...fallback];
  }

  try {
    const parsed = JSON.parse(raw) as T[];
    return Array.isArray(parsed) ? parsed : [...fallback];
  } catch {
    window.localStorage.setItem(storageKey, JSON.stringify(fallback));
    return [...fallback];
  }
}

function writeMockCollection<T>(storageKey: string, values: T[]) {
  if (!isBrowser()) {
    return;
  }

  window.localStorage.setItem(storageKey, JSON.stringify(values));
}

function getMockTeams() {
  return readMockCollection(MOCK_TEAMS_STORAGE_KEY, seededTeams);
}

function setMockTeams(nextTeams: Team[]) {
  writeMockCollection(MOCK_TEAMS_STORAGE_KEY, nextTeams);
  return nextTeams;
}

function getMockDevelopers() {
  return readMockCollection(MOCK_DEVELOPERS_STORAGE_KEY, seededDevelopers);
}

function setMockDevelopers(nextDevelopers: Developer[]) {
  writeMockCollection(MOCK_DEVELOPERS_STORAGE_KEY, nextDevelopers);
  return nextDevelopers;
}

function createMockTeam(payload: TeamCreateRequest): Team {
  const team: Team = {
    id: crypto.randomUUID(),
    name: payload.name,
    domain: payload.domain,
    sprintLengthDays: payload.sprintLengthDays,
    trelloBoardId: payload.trelloBoardId?.trim() || undefined,
    createdAt: new Date().toISOString(),
  };

  const nextTeams = [...getMockTeams(), team];
  setMockTeams(nextTeams);
  return team;
}

function createMockDeveloper(teamId: string, payload: DeveloperCreateRequest): Developer {
  const developer: Developer = {
    id: crypto.randomUUID(),
    teamId,
    name: payload.name,
    role: payload.role,
    capabilities: payload.capabilities,
    baseVelocity: payload.baseVelocity,
  };

  const nextDevelopers = [...getMockDevelopers(), developer];
  setMockDevelopers(nextDevelopers);
  return developer;
}

export const getTeams = () =>
  USE_REAL_API
    ? api.get<Team[]>('/api/teams').then((response) => response.data)
    : Promise.resolve(getMockTeams());

export const createTeam = (team: TeamCreateRequest) =>
  USE_REAL_API
    ? api.post<Team>('/api/teams', team).then((response) => response.data)
    : Promise.resolve(createMockTeam(team));

export const getDevelopers = (teamId: string) =>
  USE_REAL_API
    ? api.get<Developer[]>(`/api/teams/${teamId}/developers`).then((response) => response.data)
    : Promise.resolve(getMockDevelopers().filter((developer) => developer.teamId === teamId));

export const createDeveloper = (teamId: string, developer: DeveloperCreateRequest) =>
  USE_REAL_API
    ? api.post<Developer>(`/api/teams/${teamId}/developers`, developer).then((response) => response.data)
    : Promise.resolve(createMockDeveloper(teamId, developer));

export const removeDeveloper = (teamId: string, developerId: string) =>
  USE_REAL_API
    ? api.delete(`/api/teams/${teamId}/developers/${developerId}`).then((response) => response.data)
    : Promise.resolve().then(() => {
        const nextDevelopers = getMockDevelopers().filter((developer) => developer.id !== developerId);
        setMockDevelopers(nextDevelopers);
        return { status: 'SKIPPED_BACKEND_STEP_12', teamId, developerId };
      });

/**
 * Load sample teams and developers for rapid testing.
 * All data is ephemeral - stored in localStorage, not persisted to backend.
 * Useful for demo and testing workflows.
 */
export const getTeam = (teamId: string): Promise<Team | null> =>
  USE_REAL_API
    ? api.get<Team>(`/api/teams/${teamId}`).then((response) => response.data)
    : Promise.resolve(getMockTeams().find((team) => team.id === teamId) ?? null);

export const linkTrelloBoard = (teamId: string, boardId: string): Promise<Team> =>
  USE_REAL_API
    ? api.put<Team>(`/api/teams/${teamId}/trello-board`, { boardId }).then((response) => response.data)
    : Promise.resolve(
        (() => {
          const teams = getMockTeams();
          const updated = teams.map((t) => (t.id === teamId ? { ...t, trelloBoardId: boardId } : t));
          setMockTeams(updated);
          return updated.find((t) => t.id === teamId)!;
        })(),
      );

export const getTrelloBoards = (): Promise<{ id: string; name: string }[]> =>
  api.get<{ id: string; name: string }[]>('/api/teams/trello/boards').then((response) => response.data);

export interface TrelloMember {
  id: string;
  fullName: string;
  username: string;
}

export const getTrelloMembers = (teamId: string): Promise<TrelloMember[]> =>
  api.get<TrelloMember[]>(`/api/teams/${teamId}/trello/members`).then((r) => r.data);

export const linkTrelloMember = (teamId: string, devId: string, trelloMemberId: string): Promise<void> =>
  api.put(`/api/teams/${teamId}/developers/${devId}/trello-member`, { trelloMemberId }).then(() => undefined);

export const loadSampleData = async () => {
  if (USE_REAL_API) {
    const response = await api.post<{ teamsCreated: number; developersCreated: number; firstTeamId?: string | null }>(
      '/api/teams/sample-data',
    );
    return response.data;
  }

  const existingTeams = getMockTeams();

  // Only add sample teams if they don't already exist
  const teamsToAdd = SAMPLE_TEAMS.filter(
    (sample) => !existingTeams.some((existing) => existing.id === sample.id)
  );

  if (teamsToAdd.length > 0) {
    const updatedTeams = [...existingTeams, ...teamsToAdd];
    setMockTeams(updatedTeams);
  }

  // Add sample developers for sample teams
  const existingDevelopers = getMockDevelopers();
  const developersToAdd = SAMPLE_DEVELOPERS.filter(
    (sample) => !existingDevelopers.some((existing) => existing.id === sample.id)
  );

  if (developersToAdd.length > 0) {
    const updatedDevelopers = [...existingDevelopers, ...developersToAdd];
    setMockDevelopers(updatedDevelopers);
  }

  return {
    teamsCreated: teamsToAdd.length,
    developersCreated: developersToAdd.length,
    firstTeamId: SAMPLE_TEAMS[0]?.id ?? null,
  };
};
