import { afterEach, expect, test, vi } from 'vitest';
import {
  forgetProject,
  MAX_RECENT_PROJECTS,
  readRecentProjects,
  RECENT_PROJECTS_KEY,
  rememberProject,
} from './recentProjects';
import { pageTitle } from './title';

afterEach(() => vi.restoreAllMocks());

test('the last project opened comes first, without repeats, up to the limit', () => {
  for (let index = 1; index <= MAX_RECENT_PROJECTS + 1; index++) {
    rememberProject(`p${index}`);
  }
  rememberProject('p3');

  expect(readRecentProjects()).toEqual(['p3', 'p6', 'p5', 'p4', 'p2']);
});

test('a forgotten project leaves the list, and an empty list leaves the storage', () => {
  rememberProject('p1');
  rememberProject('p2');
  forgetProject('p1');
  expect(readRecentProjects()).toEqual(['p2']);

  forgetProject('p2');
  expect(window.localStorage.getItem(RECENT_PROJECTS_KEY)).toBeNull();
});

test('a corrupted value or blocked storage reads as an empty list', () => {
  window.localStorage.setItem(RECENT_PROJECTS_KEY, '{not json');
  expect(readRecentProjects()).toEqual([]);

  window.localStorage.setItem(RECENT_PROJECTS_KEY, JSON.stringify(['p1', 2, null]));
  expect(readRecentProjects()).toEqual(['p1']);

  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
    throw new Error('blocked');
  });
  expect(readRecentProjects()).toEqual([]);
  expect(() => rememberProject('p2')).not.toThrow();
});

test('the page title goes from the most specific part to the product name', () => {
  expect(pageTitle('Perguntar', 'sample-shop')).toBe('Perguntar · sample-shop · Codebase Copilot');
  expect(pageTitle(null, undefined)).toBe('Codebase Copilot');
});
