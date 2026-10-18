import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { page, providers, workspace } from './fixtures';

// What the shell asks on every screen; a test overrides any of them with server.use.
const shellDefaults = [
  http.get('/api/v1/system/providers', () => HttpResponse.json(providers())),
  http.get('/api/v1/workspaces', () => HttpResponse.json(page([]))),
  http.get('/api/v1/workspaces/:workspaceId', ({ params }) =>
    HttpResponse.json(workspace({ id: String(params.workspaceId) })),
  ),
  http.get('/api/v1/workspaces/:workspaceId/projects', () => HttpResponse.json(page([]))),
  http.get('/api/v1/projects/:projectId/conversations', () => HttpResponse.json(page([]))),
];

export const server = setupServer(...shellDefaults);
