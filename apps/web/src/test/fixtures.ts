import type {
  Answer,
  Conversation,
  Evidence,
  EvidenceDetail,
  FileContent,
} from '../features/ask/api';
import type { ImportJob, Inventory, Providers, Settings } from '../features/import/api';
import type { IndexJob, Snapshot, Usage } from '../features/project/api';
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
    lastJob: null,
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

export function indexJob(projectId: string, overrides: Partial<IndexJob> = {}): IndexJob {
  return {
    id: '0192f5c4-0000-7000-8000-000000000301',
    projectId,
    snapshotId: '0192f5c4-0000-7000-8000-000000000201',
    status: 'PENDING',
    stage: null,
    progress: null,
    attempts: 0,
    error: null,
    cancelRequested: false,
    createdAt: '2026-10-16T12:00:00Z',
    startedAt: null,
    completedAt: null,
    ...overrides,
  };
}

export function snapshotDetail(overrides: Partial<Snapshot> = {}): Snapshot {
  return {
    id: '0192f5c4-0000-7000-8000-000000000201',
    projectId: '0192f5c4-0000-7000-8000-000000000101',
    status: 'READY',
    active: true,
    settingsVersion: 0,
    chunkingVersion: 'lines-v1',
    sourceImportId: null,
    counts: {
      files: 10,
      indexedFiles: 9,
      unsupportedFiles: 1,
      failedFiles: 0,
      chunks: 40,
      bytes: 2048,
    },
    changes: null,
    inventory: null,
    embedding: null,
    lexical: { version: 'ident-v1' },
    createdAt: '2026-10-16T12:00:00Z',
    completedAt: '2026-10-16T12:05:00Z',
    prunedAt: null,
    ...overrides,
  };
}

const ANSWER_ID = '0192f5c4-0000-7000-8000-000000000601';
const CONVERSATION_ID = '0192f5c4-0000-7000-8000-000000000501';

export function evidence(overrides: Partial<Evidence> = {}): Evidence {
  return {
    id: '0192f5c4-0000-7000-8000-000000000701',
    citationLabel: 'E1',
    path: 'src/billing/Refund.java',
    otherPaths: [],
    startLine: 1,
    endLine: 40,
    citedStartLine: 12,
    citedEndLine: 14,
    snippet: 'class Refund {}',
    validationStatus: 'CITED',
    rank: 1,
    ranks: { lexical: 2, vector: 1, path: null },
    ...overrides,
  };
}

export function answer(overrides: Partial<Answer> = {}): Answer {
  return {
    answerId: ANSWER_ID,
    conversationId: CONVERSATION_ID,
    snapshotId: '0192f5c4-0000-7000-8000-000000000201',
    question: 'Como o reembolso é calculado?',
    status: 'ANSWERED',
    answer: 'O reembolso é o total menos a taxa [E1].',
    warnings: [],
    invalidCitationCount: 0,
    error: null,
    evidence: [evidence()],
    model: { provider: 'fake', name: 'fake' },
    retrieval: { mode: 'HYBRID', promptVersion: 'prompt-v1' },
    metrics: {
      latencyMs: 1840,
      retrievalMs: 120,
      generationMs: 1690,
      inputTokens: 4210,
      outputTokens: 380,
      estimatedCost: 0.000611,
      costCurrency: 'USD',
      costStatus: 'ESTIMATED',
    },
    createdAt: '2026-10-16T12:00:00Z',
    completedAt: '2026-10-16T12:00:02Z',
    ...overrides,
  };
}

export function conversation(answers: Answer[] = [answer()]): Conversation {
  return {
    conversation: {
      id: CONVERSATION_ID,
      projectId: '0192f5c4-0000-7000-8000-000000000101',
      title: answers[0]?.question ?? 'Conversa',
      questionCount: answers.length,
      createdAt: '2026-10-16T12:00:00Z',
      updatedAt: '2026-10-16T12:00:02Z',
    },
    answers,
  };
}

export function evidenceDetail(overrides: Partial<EvidenceDetail> = {}): EvidenceDetail {
  const { otherPaths, ...base } = evidence();
  return {
    ...base,
    otherPaths,
    answerId: ANSWER_ID,
    snapshotId: '0192f5c4-0000-7000-8000-000000000201',
    snapshotStatus: 'AVAILABLE',
    activeVersion: 'SAME',
    contentHash: 'hash-1',
    ...overrides,
  };
}

export function fileContent(overrides: Partial<FileContent> = {}): FileContent {
  const content = Array.from({ length: 40 }, (_, index) => `line ${index + 1}`).join('\n') + '\n';
  return {
    snapshotId: '0192f5c4-0000-7000-8000-000000000201',
    path: 'src/billing/Refund.java',
    language: 'java',
    contentHash: 'hash-1',
    sizeBytes: content.length,
    lineCount: 40,
    content,
    ...overrides,
  };
}

export function usage(overrides: Partial<Usage> = {}): Usage {
  return {
    indexing: {
      total: 3,
      completed: 2,
      failed: 1,
      cancelled: 0,
      averageDurationMs: 1500,
      p95DurationMs: 2100,
    },
    questions: {
      total: 4,
      answered: 2,
      partial: 1,
      abstained: 1,
      failed: 0,
      pending: 0,
      p50LatencyMs: 820,
      p95LatencyMs: 1900,
    },
    cost: {
      inputTokens: 12000,
      outputTokens: 800,
      estimatedCost: 0.0125,
      currency: 'USD',
      unknownCostAnswers: 1,
    },
    ...overrides,
  };
}
