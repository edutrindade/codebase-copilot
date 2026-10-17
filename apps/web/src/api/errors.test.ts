import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import { problem } from '../test/problems';
import { server } from '../test/server';
import { api } from './client';
import { ApiError, unwrap } from './errors';

const workspaces = '/api/v1/workspaces';

describe('unwrap', () => {
  test('returns the body of a successful response', async () => {
    server.use(
      http.get(workspaces, () =>
        HttpResponse.json({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }),
      ),
    );

    const page = await unwrap(api.GET('/api/v1/workspaces'));

    expect(page.totalItems).toBe(0);
  });

  test('turns a ProblemDetail into an ApiError with its code', async () => {
    server.use(http.get(workspaces, () => problem(409, 'WORKSPACE_NOT_EMPTY')));

    const error = await unwrap(api.GET('/api/v1/workspaces')).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(409);
    expect(apiError.code).toBe('WORKSPACE_NOT_EMPTY');
    expect(apiError.problem?.requestId).toBe('00000000-0000-4000-8000-000000000001');
  });

  test('keeps the status when the error body is not a ProblemDetail', async () => {
    server.use(http.get(workspaces, () => new HttpResponse('Bad Gateway', { status: 502 })));

    const error = (await unwrap(api.GET('/api/v1/workspaces')).catch(
      (e: unknown) => e,
    )) as ApiError;

    expect(error.status).toBe(502);
    expect(error.problem).toBeNull();
    expect(error.isNetworkError).toBe(false);
  });

  test('reports a failed connection as a network error', async () => {
    server.use(http.get(workspaces, () => HttpResponse.error()));

    const error = (await unwrap(api.GET('/api/v1/workspaces')).catch(
      (e: unknown) => e,
    )) as ApiError;

    expect(error.isNetworkError).toBe(true);
    expect(error.code).toBeNull();
  });

  test('lets an aborted request propagate as an abort', async () => {
    server.use(http.get(workspaces, () => new Promise<never>(() => {})));
    const controller = new AbortController();

    const pending = unwrap(api.GET('/api/v1/workspaces', { signal: controller.signal }));
    controller.abort();

    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
  });
});
