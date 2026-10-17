import { api } from '../../api/client';
import { unwrap } from '../../api/errors';
import type { components } from '../../api/schema';

export type Workspace = components['schemas']['WorkspaceResponse'];
export type Project = components['schemas']['ProjectResponse'];

export const workspaceKeys = {
  all: ['workspaces'] as const,
  list: (page: number) => ['workspaces', 'list', page] as const,
  detail: (workspaceId: string) => ['workspaces', workspaceId] as const,
  projects: (workspaceId: string, page: number) =>
    ['workspaces', workspaceId, 'projects', page] as const,
};

export function listWorkspaces(page: number, signal: AbortSignal) {
  return unwrap(api.GET('/api/v1/workspaces', { params: { query: { page } }, signal }));
}

export function getWorkspace(workspaceId: string, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/workspaces/{workspaceId}', { params: { path: { workspaceId } }, signal }),
  );
}

export function createWorkspace(name: string) {
  const trimmed = name.trim();
  return unwrap(
    api.POST('/api/v1/workspaces', { body: { name: trimmed === '' ? null : trimmed } }),
  );
}

export function deleteWorkspace(workspaceId: string) {
  return unwrap(
    api.DELETE('/api/v1/workspaces/{workspaceId}', { params: { path: { workspaceId } } }),
  );
}

export function listProjects(workspaceId: string, page: number, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/workspaces/{workspaceId}/projects', {
      params: { path: { workspaceId }, query: { page } },
      signal,
    }),
  );
}

export function deleteProject(projectId: string) {
  return unwrap(api.DELETE('/api/v1/projects/{projectId}', { params: { path: { projectId } } }));
}
