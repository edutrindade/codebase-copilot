import type { ImportJob, Inventory, Providers, Settings } from '../features/import/api';
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

export function inventory(overrides: Partial<Inventory> = {}): Inventory {
  return {
    projectId: '0192f5c4-0000-7000-8000-000000000101',
    scannedAt: '2026-10-16T12:00:00Z',
    settingsVersion: 0,
    truncated: false,
    totals: {
      files: 1240,
      includedFiles: 980,
      includedBytes: 7340032,
      excludedFiles: 200,
      unsupportedFiles: 50,
      unreadableFiles: 10,
      excludedDirectories: 4,
    },
    byLanguage: [{ language: 'java', files: 420, bytes: 3145728 }],
    byDirectory: [
      { path: '', includedFiles: 3, excludedFiles: 0, unsupportedFiles: 0, unreadableFiles: 0 },
      {
        path: 'src',
        includedFiles: 900,
        excludedFiles: 12,
        unsupportedFiles: 3,
        unreadableFiles: 0,
      },
    ],
    exclusions: [
      {
        reason: 'DEFAULT_RULE',
        ruleId: 'node-modules',
        pattern: null,
        files: 0,
        directories: 1,
        examples: ['web/node_modules'],
      },
    ],
    warnings: [],
    ...overrides,
  };
}

export function settings(overrides: Partial<Settings> = {}): Settings {
  return {
    excludePatterns: [],
    disabledDefaultRules: [],
    maxFileBytes: null,
    maxProjectBytes: null,
    effectiveMaxFileBytes: 1048576,
    effectiveMaxProjectBytes: 524288000,
    defaultRules: [
      {
        id: 'env-files',
        category: 'SENSITIVE',
        patterns: ['.env', '.env.*'],
        exceptions: [],
        overridable: false,
        disabled: false,
      },
      {
        id: 'vendor',
        category: 'DEPENDENCIES',
        patterns: ['vendor/'],
        exceptions: [],
        overridable: true,
        disabled: false,
      },
    ],
    version: 0,
    updatedAt: null,
    ...overrides,
  };
}

export function providers(remote = false): Providers {
  return remote
    ? {
        embeddings: {
          provider: 'openai',
          model: 'text-embedding-3-small',
          dimensions: 1536,
          remote: true,
        },
        generation: { provider: 'openai', model: 'gpt-6-luna', remote: true },
      }
    : {
        embeddings: { provider: 'fake', model: 'fake', dimensions: 8, remote: false },
        generation: { provider: 'fake', model: 'fake', remote: false },
      };
}

export function importJob(overrides: Partial<ImportJob> = {}): ImportJob {
  return {
    id: '0192f5c4-0000-7000-8000-000000000401',
    projectId: '0192f5c4-0000-7000-8000-000000000101',
    status: 'PENDING',
    compressedBytes: 2048,
    uncompressedBytes: null,
    entryCount: null,
    error: null,
    createdAt: '2026-10-16T12:00:00Z',
    completedAt: null,
    ...overrides,
  };
}

export function indexJob(projectId: string) {
  return {
    id: '0192f5c4-0000-7000-8000-000000000301',
    projectId,
    snapshotId: '0192f5c4-0000-7000-8000-000000000201',
    status: 'PENDING' as const,
    stage: null,
    progress: null,
    attempts: 0,
    error: null,
    createdAt: '2026-10-16T12:00:00Z',
    startedAt: null,
    completedAt: null,
  };
}
