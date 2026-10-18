import { readStorage, writeStorage } from './storage';

export const RECENT_PROJECTS_KEY = 'recent-projects';
export const MAX_RECENT_PROJECTS = 5;

export function readRecentProjects(): string[] {
  const stored = readStorage(RECENT_PROJECTS_KEY);
  if (stored === null) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(stored);
    return Array.isArray(parsed)
      ? parsed.filter((id): id is string => typeof id === 'string').slice(0, MAX_RECENT_PROJECTS)
      : [];
  } catch {
    return [];
  }
}

export function rememberProject(projectId: string): void {
  const others = readRecentProjects().filter((id) => id !== projectId);
  write([projectId, ...others].slice(0, MAX_RECENT_PROJECTS));
}

export function forgetProject(projectId: string): void {
  const current = readRecentProjects();
  if (current.includes(projectId)) {
    write(current.filter((id) => id !== projectId));
  }
}

function write(ids: string[]) {
  writeStorage(RECENT_PROJECTS_KEY, ids.length === 0 ? null : JSON.stringify(ids));
}
