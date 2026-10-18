import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, test } from 'vitest';

const read = (path: string) => readFileSync(resolve(import.meta.dirname, path), 'utf8');
const layerStatement = (css: string) => /@layer [a-z, ]+;/.exec(css)?.[0];

test('the layer order loads before any bundled stylesheet and matches index.css', () => {
  const html = read('../../index.html');
  const statement = layerStatement(read('../../public/layers.css'));

  expect(statement).toBe(layerStatement(read('./index.css')));
  expect(html.indexOf('href="/layers.css"')).toBeGreaterThan(-1);
  expect(html.indexOf('href="/layers.css"')).toBeLessThan(html.indexOf('/src/main.tsx'));
});
