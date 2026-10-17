import { defineConfig, devices } from '@playwright/test';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

// Not 8080, so a backend already running for development is left alone.
const backendPort = Number(process.env.E2E_BACKEND_PORT ?? 8089);
// 4173 is one of the origins the backend accepts by default (D-072).
const uiUrl = 'http://127.0.0.1:4173';

export default defineConfig({
  testDir: 'e2e',
  // Needs the backend to serve the UI (playwright.jar.config.ts).
  testIgnore: ['packaged.spec.ts'],
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 180_000,
  expect: { timeout: 15_000 },
  forbidOnly: !!process.env.CI,
  reporter: [['list']],
  use: {
    baseURL: uiUrl,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      // The real backend with the Compose database (.env) and fake providers (P10).
      command: './mvnw -q spring-boot:run',
      cwd: resolve(import.meta.dirname, '../backend'),
      url: `http://127.0.0.1:${backendPort}/actuator/health`,
      env: {
        SERVER_PORT: String(backendPort),
        PROJECTS_ROOT: resolve(import.meta.dirname, 'e2e/fixtures'),
        PROJECTS_ROOT_LABEL: 'Fixtures',
        DATA_DIR: join(tmpdir(), 'codebase-copilot-e2e'),
        EMBEDDING_PROVIDER: 'fake',
        GENERATION_PROVIDER: 'fake',
      },
      reuseExistingServer: false,
      timeout: 240_000,
      stdout: 'ignore',
      stderr: 'pipe',
      gracefulShutdown: { signal: 'SIGTERM', timeout: 10_000 },
    },
    {
      command: 'npx vite preview --host 127.0.0.1',
      url: uiUrl,
      env: { BACKEND_URL: `http://127.0.0.1:${backendPort}` },
      reuseExistingServer: false,
      timeout: 60_000,
    },
  ],
});
