import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './server';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
  // The shell keeps preferences there (last workspace, recent projects, collapsed sidebar).
  if (typeof window !== 'undefined') {
    window.localStorage.clear();
  }
});
afterAll(() => server.close());
