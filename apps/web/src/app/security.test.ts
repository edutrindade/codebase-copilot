// @vitest-environment node
import { ESLint } from 'eslint';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from 'vitest';
import { contentSecurityPolicy } from '../../csp';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');

test('the content security policy allows only same-origin scripts', () => {
  const directives = contentSecurityPolicy.split('; ');

  expect(directives).toContain("default-src 'self'");
  expect(directives).toContain("script-src 'self'");
  expect(directives).toContain("object-src 'none'");
  expect(contentSecurityPolicy).not.toMatch(/unsafe-inline|unsafe-eval/);
});

test('lint rejects dangerouslySetInnerHTML and console calls', async () => {
  const eslint = new ESLint({ cwd: webRoot });
  const source = [
    'export function Unsafe({ html }: { html: string }) {',
    "  console.log('rendering');",
    '  return <div dangerouslySetInnerHTML={{ __html: html }} />;',
    '}',
    '',
  ].join('\n');

  const [result] = await eslint.lintText(source, { filePath: `${webRoot}/src/Unsafe.tsx` });

  const rules = result?.messages.map((message) => message.ruleId);
  expect(rules).toContain('no-restricted-syntax');
  expect(rules).toContain('no-console');
});
