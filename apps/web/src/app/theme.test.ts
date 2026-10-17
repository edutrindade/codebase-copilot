import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { afterEach, expect, test, vi } from 'vitest';
import { applyTheme, readThemePreference, setThemePreference, THEME_STORAGE_KEY } from './theme';

afterEach(() => {
  setThemePreference('system');
  vi.restoreAllMocks();
});

test('an explicit theme is saved and set on the root element', () => {
  setThemePreference('dark');

  expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  expect(document.documentElement.dataset.theme).toBe('dark');
  expect(readThemePreference()).toBe('dark');
});

test('the system theme clears both', () => {
  setThemePreference('light');
  setThemePreference('system');

  expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBeNull();
  expect(document.documentElement.dataset.theme).toBeUndefined();
});

test('an unknown saved value means the system theme', () => {
  window.localStorage.setItem(THEME_STORAGE_KEY, 'sepia');

  expect(readThemePreference()).toBe('system');
});

test('blocked storage still applies the theme to the page', () => {
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
    throw new Error('blocked');
  });

  setThemePreference('dark');

  expect(document.documentElement.dataset.theme).toBe('dark');
  applyTheme('system');
});

test('the script that runs before the first paint reads the same key', () => {
  const script = readFileSync(resolve(import.meta.dirname, '../../public/theme-init.js'), 'utf8');

  expect(script).toContain(`getItem('${THEME_STORAGE_KEY}')`);
});
