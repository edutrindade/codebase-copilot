import { HttpResponse } from 'msw';
import type { ErrorCode, Problem } from '../api/errors';

export function problem(status: number, code: ErrorCode, overrides: Partial<Problem> = {}) {
  return HttpResponse.json<Problem>(
    {
      type: 'about:blank',
      title: 'Título do erro',
      status,
      detail: 'Detalhe do erro.',
      instance: '/api/v1/test',
      code,
      requestId: '00000000-0000-4000-8000-000000000001',
      ...overrides,
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}
