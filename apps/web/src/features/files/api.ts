import { api } from '../../api/client';
import { unwrap } from '../../api/errors';
import type { components } from '../../api/schema';

type Schemas = components['schemas'];
export type TreeEntry = Schemas['TreeEntryResponse'];
export type FileStatus = 'INDEXED' | 'UNSUPPORTED' | 'FAILED';
export type FileFilter = 'ALL' | FileStatus;

export const FILE_FILTERS: readonly FileFilter[] = ['ALL', 'INDEXED', 'UNSUPPORTED', 'FAILED'];

export const filesKeys = {
  tree: (snapshotId: string, filter: FileFilter, path: string) =>
    ['snapshots', snapshotId, 'tree', filter, path] as const,
  file: (snapshotId: string, path: string) => ['snapshots', snapshotId, 'file', path] as const,
};

export function getTree(snapshotId: string, filter: FileFilter, path: string, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/snapshots/{snapshotId}/tree', {
      params: {
        path: { snapshotId },
        query: { path: path || undefined, status: filter === 'ALL' ? undefined : filter },
      },
      signal,
    }),
  );
}

// The listing filters by prefix, so the exact path comes first when it is a file.
export async function getFileInfo(snapshotId: string, path: string, signal: AbortSignal) {
  const files = await unwrap(
    api.GET('/api/v1/snapshots/{snapshotId}/files', {
      params: { path: { snapshotId }, query: { pathPrefix: path, page: 0, size: 1 } },
      signal,
    }),
  );
  const first = files.items[0];
  return first?.relativePath === path ? first : null;
}
