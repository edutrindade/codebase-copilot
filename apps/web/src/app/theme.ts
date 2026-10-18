import { useSyncExternalStore } from 'react';

export type ThemePreference = 'system' | 'light' | 'dark';

// public/theme-init.js reads the same key before the first paint; a test keeps both in sync.
export const THEME_STORAGE_KEY = 'theme';

const listeners = new Set<() => void>();

function isExplicit(value: string | null): value is 'light' | 'dark' {
  return value === 'light' || value === 'dark';
}

export function readThemePreference(): ThemePreference {
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isExplicit(stored) ? stored : 'system';
  } catch {
    return 'system';
  }
}

export function applyTheme(preference: ThemePreference): void {
  const root = document.documentElement;
  if (preference === 'system') {
    delete root.dataset.theme;
  } else {
    root.dataset.theme = preference;
  }
}

export function setThemePreference(preference: ThemePreference): void {
  try {
    if (preference === 'system') {
      window.localStorage.removeItem(THEME_STORAGE_KEY);
    } else {
      window.localStorage.setItem(THEME_STORAGE_KEY, preference);
    }
  } catch {
    // Storage unavailable: the choice lasts until the page is reloaded.
  }
  applyTheme(preference);
  listeners.forEach((listener) => listener());
}

function subscribe(onChange: () => void) {
  listeners.add(onChange);
  // Another tab changed the theme.
  const onStorage = (event: StorageEvent) => {
    if (event.key === THEME_STORAGE_KEY) {
      applyTheme(readThemePreference());
      onChange();
    }
  };
  window.addEventListener('storage', onStorage);
  return () => {
    listeners.delete(onChange);
    window.removeEventListener('storage', onStorage);
  };
}

function currentPreference(): ThemePreference {
  const theme = document.documentElement.dataset.theme ?? null;
  return isExplicit(theme) ? theme : 'system';
}

export function useThemePreference(): ThemePreference {
  return useSyncExternalStore(subscribe, currentPreference);
}
