import { describe, expect, test } from 'vitest';
import { messages } from '../messages';
import type { Problem } from './errors';
import { ApiError } from './errors';
import { recoveryByCode, recoveryFor } from './recovery';

function apiError(status: number, code: Problem['code']) {
  return new ApiError(status, {
    type: 'about:blank',
    title: 't',
    status,
    detail: 'd',
    instance: '/api/v1/test',
    code,
    requestId: '00000000-0000-4000-8000-000000000001',
  });
}

describe('recoveryFor', () => {
  test.each([
    [409, 'WORKSPACE_NOT_EMPTY', 'removeProjects'],
    [409, 'PROJECT_BUSY', 'waitForJob'],
    [404, 'PROJECT_NOT_FOUND', 'goBack'],
    [400, 'VALIDATION_FAILED', 'fixInput'],
    [500, 'INTERNAL_ERROR', 'retry'],
    [503, 'GENERATION_NOT_AVAILABLE', 'configureProvider'],
    [429, 'EMBEDDING_RATE_LIMITED', 'providerLimit'],
    [409, 'SNAPSHOT_EMBEDDING_MISMATCH', 'reindex'],
    [403, 'FORBIDDEN_ORIGIN', 'openLocally'],
  ] as const)('%i %s → %s', (status, code, recovery) => {
    expect(recoveryFor(apiError(status, code))).toBe(recovery);
  });

  test('retries network failures and server errors without a ProblemDetail', () => {
    expect(recoveryFor(new ApiError(0, null))).toBe('retry');
    expect(recoveryFor(new ApiError(502, null))).toBe('retry');
  });

  test('asks for a reload on anything else', () => {
    expect(recoveryFor(new ApiError(400, null))).toBe('reload');
    expect(recoveryFor(new TypeError('boom'))).toBe('reload');
  });

  test('every recovery in the table has a message', () => {
    for (const recovery of Object.values(recoveryByCode)) {
      expect(messages.recovery[recovery]).toBeTruthy();
    }
  });
});
