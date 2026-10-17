// @vitest-environment node
import { execFileSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from 'vitest';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');

test('generated API types match apps/backend/openapi.json', () => {
  const check = () =>
    execFileSync(
      process.execPath,
      [
        'node_modules/openapi-typescript/bin/cli.js',
        '../backend/openapi.json',
        '-o',
        'src/api/schema.d.ts',
        '--check',
      ],
      { cwd: webRoot, stdio: 'pipe' },
    );

  expect(check, 'src/api/schema.d.ts is stale: run npm run api:types').not.toThrow();
});
