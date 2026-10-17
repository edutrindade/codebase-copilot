import { defineConfig } from '@playwright/test';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import base from './playwright.config';

// The same specs against the packaged jar (P8 of Etapa 8): UI and API on one origin, with the
// headers sent by the backend. npm run e2e:jar builds the jar with -Pwith-ui first.
const backendPort = Number(process.env.E2E_BACKEND_PORT ?? 8089);
const backendUrl = `http://127.0.0.1:${backendPort}`;

export default defineConfig({
  ...base,
  testIgnore: [],
  use: { ...base.use, baseURL: backendUrl },
  webServer: [
    {
      // Run from apps/backend, so the jar finds the Compose database settings in ../../.env.
      command: 'java -jar target/backend-0.0.1-SNAPSHOT.jar',
      cwd: resolve(import.meta.dirname, '../backend'),
      url: `${backendUrl}/actuator/health`,
      env: {
        SERVER_PORT: String(backendPort),
        PROJECTS_ROOT: resolve(import.meta.dirname, 'e2e/fixtures'),
        PROJECTS_ROOT_LABEL: 'Fixtures',
        DATA_DIR: join(tmpdir(), 'codebase-copilot-e2e'),
        EMBEDDING_PROVIDER: 'fake',
        GENERATION_PROVIDER: 'fake',
      },
      reuseExistingServer: false,
      timeout: 120_000,
      stdout: 'ignore',
      stderr: 'pipe',
      gracefulShutdown: { signal: 'SIGTERM', timeout: 10_000 },
    },
  ],
});
