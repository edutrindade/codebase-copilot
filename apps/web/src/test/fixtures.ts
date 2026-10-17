import type { Project, Workspace } from '../features/workspaces/api';

export function page<T>(items: T[], overrides: { page?: number; totalPages?: number } = {}) {
  return {
    items,
    page: overrides.page ?? 0,
    size: 20,
    totalItems: items.length,
    totalPages: overrides.totalPages ?? (items.length === 0 ? 0 : 1),
  };
}

export function workspace(overrides: Partial<Workspace> = {}): Workspace {
  return {
    id: '0192f5c4-0000-7000-8000-000000000001',
    name: 'Meus projetos',
    projectCount: 0,
    createdAt: '2026-10-16T12:00:00Z',
    ...overrides,
  };
}

export function project(overrides: Partial<Project> = {}): Project {
  return {
    id: '0192f5c4-0000-7000-8000-000000000101',
    workspaceId: '0192f5c4-0000-7000-8000-000000000001',
    name: 'codebase-copilot',
    source: { type: 'LOCAL_FOLDER', rootKey: 'default', relativePath: 'codebase-copilot' },
    activeSnapshot: null,
    indexing: null,
    outdatedReasons: [],
    createdAt: '2026-10-16T12:00:00Z',
    ...overrides,
  };
}

export function readySnapshot(
  status: 'READY' | 'PARTIAL' = 'READY',
): NonNullable<Project['activeSnapshot']> {
  return {
    id: '0192f5c4-0000-7000-8000-000000000201',
    status,
    completedAt: '2026-10-16T12:05:00Z',
    counts: {
      files: 10,
      indexedFiles: 9,
      unsupportedFiles: 1,
      failedFiles: 0,
      chunks: 40,
      bytes: 2048,
    },
  };
}
