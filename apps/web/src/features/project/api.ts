import { api } from '../../api/client';
import { unwrap } from '../../api/errors';
import type { components } from '../../api/schema';

type Schemas = components['schemas'];
export type IndexJob = Schemas['IndexJobResponse'];
export type Indexing = Schemas['IndexingResponse'];
export type Snapshot = Schemas['SnapshotResponse'];
export type SnapshotFile = Schemas['SnapshotFileResponse'];
export type UnindexedStatus = 'FAILED' | 'UNSUPPORTED';

export const projectKeys = {
  latestJob: (projectId: string) => ['projects', projectId, 'index-jobs', 'latest'] as const,
  snapshots: (projectId: string, page: number) =>
    ['projects', projectId, 'snapshots', page] as const,
  snapshot: (snapshotId: string) => ['snapshots', snapshotId] as const,
  files: (snapshotId: string, status: UnindexedStatus, page: number) =>
    ['snapshots', snapshotId, 'files', status, page] as const,
};

export async function getLatestIndexJob(projectId: string, signal: AbortSignal) {
  const jobs = await unwrap(
    api.GET('/api/v1/projects/{projectId}/index-jobs', {
      params: { path: { projectId }, query: { page: 0, size: 1 } },
      signal,
    }),
  );
  return jobs.items[0] ?? null;
}

export function listSnapshots(projectId: string, page: number, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/projects/{projectId}/snapshots', {
      params: { path: { projectId }, query: { page, size: 10 } },
      signal,
    }),
  );
}

export function getSnapshot(snapshotId: string, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/snapshots/{snapshotId}', { params: { path: { snapshotId } }, signal }),
  );
}

export function listUnindexedFiles(
  snapshotId: string,
  status: UnindexedStatus,
  page: number,
  signal: AbortSignal,
) {
  return unwrap(
    api.GET('/api/v1/snapshots/{snapshotId}/files', {
      params: { path: { snapshotId }, query: { status, page } },
      signal,
    }),
  );
}
