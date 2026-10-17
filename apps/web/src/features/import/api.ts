import { api } from '../../api/client';
import { unwrap } from '../../api/errors';
import type { components } from '../../api/schema';

type Schemas = components['schemas'];
export type LocalRoot = Schemas['LocalRootResponse'];
export type DirectoryListing = Schemas['DirectoryListingResponse'];
export type ImportJob = Schemas['ImportResponse'];
export type Inventory = Schemas['InventoryResponse'];
export type ExclusionGroup = Schemas['ExclusionGroupResponse'];
export type InventoryWarning = Schemas['InventoryWarningResponse'];
export type Settings = Schemas['SettingsResponse'];
export type SettingsUpdate = Schemas['UpdateSettingsRequest'];
export type DefaultRule = Schemas['DefaultRuleResponse'];
export type Providers = Schemas['ProvidersResponse'];

export type NewProject =
  | { sourceType: 'LOCAL_FOLDER'; name: string; rootKey: string; relativePath: string }
  | { sourceType: 'ZIP'; name: string };

export const importKeys = {
  roots: ['local-roots'] as const,
  directories: (rootKey: string, path: string) => ['local-roots', rootKey, path] as const,
  project: (projectId: string) => ['projects', projectId] as const,
  latestImport: (projectId: string) => ['projects', projectId, 'imports', 'latest'] as const,
  inventory: (projectId: string) => ['projects', projectId, 'inventory'] as const,
  settings: (projectId: string) => ['projects', projectId, 'settings'] as const,
  providers: ['system', 'providers'] as const,
};

export function listLocalRoots(signal: AbortSignal) {
  return unwrap(api.GET('/api/v1/local-roots', { signal }));
}

export function listDirectories(rootKey: string, path: string, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/local-roots/{rootKey}/directories', {
      params: { path: { rootKey }, query: { path } },
      signal,
    }),
  );
}

export function createProject(workspaceId: string, project: NewProject) {
  return unwrap(
    api.POST('/api/v1/workspaces/{workspaceId}/projects', {
      params: { path: { workspaceId } },
      body: project,
    }),
  );
}

export function getProject(projectId: string, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/projects/{projectId}', { params: { path: { projectId } }, signal }),
  );
}

export function uploadZip(projectId: string, file: File) {
  const form = new FormData();
  form.append('file', file);
  return unwrap(
    api.POST('/api/v1/projects/{projectId}/imports/zip', {
      params: { path: { projectId } },
      body: {},
      bodySerializer: () => form,
    }),
  );
}

export async function getLatestImport(projectId: string, signal: AbortSignal) {
  const imports = await unwrap(
    api.GET('/api/v1/projects/{projectId}/imports', {
      params: { path: { projectId }, query: { page: 0, size: 1 } },
      signal,
    }),
  );
  return imports.items[0] ?? null;
}

export function getInventory(projectId: string, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/projects/{projectId}/inventory', { params: { path: { projectId } }, signal }),
  );
}

export function getSettings(projectId: string, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/projects/{projectId}/settings', { params: { path: { projectId } }, signal }),
  );
}

export function updateSettings(projectId: string, settings: SettingsUpdate) {
  return unwrap(
    api.PUT('/api/v1/projects/{projectId}/settings', {
      params: { path: { projectId } },
      body: settings,
    }),
  );
}

export function getProviders(signal: AbortSignal) {
  return unwrap(api.GET('/api/v1/system/providers', { signal }));
}

export function startIndexing(projectId: string) {
  return unwrap(
    api.POST('/api/v1/projects/{projectId}/index-jobs', { params: { path: { projectId } } }),
  );
}
