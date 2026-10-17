import { expect, test } from 'vitest';
import { project, readySnapshot } from '../../test/fixtures';
import { projectStatus } from './projectStatus';

const indexing = {
  id: '0192f5c4-0000-7000-8000-000000000301',
  status: 'RUNNING' as const,
  stage: 'PROCESSING_FILES' as const,
  progress: { total: 10, completed: 3 },
  cancelRequested: false,
};

test('an active job wins over everything else', () => {
  expect(projectStatus(project({ indexing, activeSnapshot: readySnapshot() }))).toBe('INDEXING');
});

test('a project without a snapshot is not indexed, or awaits its ZIP', () => {
  expect(projectStatus(project())).toBe('NOT_INDEXED');
  expect(projectStatus(project({ source: { type: 'ZIP', currentImport: null } }))).toBe(
    'AWAITING_UPLOAD',
  );
  expect(
    projectStatus(
      project({
        source: {
          type: 'ZIP',
          currentImport: { id: '0192f5c4-0000-7000-8000-000000000401', status: 'READY' },
        },
      }),
    ),
  ).toBe('NOT_INDEXED');
});

test('outdated reasons win over the snapshot status', () => {
  expect(
    projectStatus(
      project({ activeSnapshot: readySnapshot('PARTIAL'), outdatedReasons: ['SETTINGS_CHANGED'] }),
    ),
  ).toBe('OUTDATED');
});

test('a current snapshot is ready or partial', () => {
  expect(projectStatus(project({ activeSnapshot: readySnapshot() }))).toBe('READY');
  expect(projectStatus(project({ activeSnapshot: readySnapshot('PARTIAL') }))).toBe('PARTIAL');
});

test('a failed last job is shown until the next one starts', () => {
  const lastJob = {
    id: '0192f5c4-0000-7000-8000-000000000302',
    status: 'FAILED' as const,
    finishedAt: '2026-10-16T12:05:00Z',
    errorCode: 'INDEX_IO_ERROR',
  };
  expect(projectStatus(project({ lastJob, activeSnapshot: readySnapshot() }))).toBe('FAILED');
  expect(projectStatus(project({ lastJob }))).toBe('FAILED');
  expect(projectStatus(project({ lastJob, indexing }))).toBe('INDEXING');
  expect(
    projectStatus(
      project({
        lastJob: { ...lastJob, status: 'CANCELLED', errorCode: null },
        activeSnapshot: readySnapshot(),
      }),
    ),
  ).toBe('READY');
});
