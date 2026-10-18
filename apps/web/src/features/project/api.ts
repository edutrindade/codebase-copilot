import { api } from '../../api/client';
import { unwrap } from '../../api/errors';
import type { components } from '../../api/schema';

type Schemas = components['schemas'];
export type IndexJob = Schemas['IndexJobResponse'];
export type Indexing = Schemas['IndexingResponse'];
export type Snapshot = Schemas['SnapshotResponse'];
export type Usage = Schemas['UsageResponse'];

export const projectKeys = {
  latestJob: (projectId: string) => ['projects', projectId, 'index-jobs', 'latest'] as const,
  usage: (projectId: string) => ['projects', projectId, 'usage'] as const,
  snapshots: (projectId: string, page: number) =>
    ['projects', projectId, 'snapshots', page] as const,
  snapshot: (snapshotId: string) => ['snapshots', snapshotId] as const,
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

export function cancelIndexJob(jobId: string) {
  return unwrap(api.POST('/api/v1/index-jobs/{jobId}/cancel', { params: { path: { jobId } } }));
}

export function getUsage(projectId: string, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/projects/{projectId}/usage', { params: { path: { projectId } }, signal }),
  );
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
