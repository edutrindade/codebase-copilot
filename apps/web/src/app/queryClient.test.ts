import { expect, test } from 'vitest';
import { ApiError } from '../api/errors';
import { shouldRetry } from './queryClient';

test('does not retry client errors', () => {
  expect(shouldRetry(0, new ApiError(404, null))).toBe(false);
});

test('retries server and network errors a limited number of times', () => {
  expect(shouldRetry(0, new ApiError(503, null))).toBe(true);
  expect(shouldRetry(1, new ApiError(0, null))).toBe(true);
  expect(shouldRetry(2, new ApiError(0, null))).toBe(false);
});
