import { useEffect, useRef, useSyncExternalStore } from 'react';

export type ShortcutAction =
  | 'openPalette'
  | 'showShortcuts'
  | 'focusQuestion'
  | 'previousEvidence'
  | 'nextEvidence'
  | 'goOverview'
  | 'goAsk'
  | 'goFiles'
  | 'goHistory';

type Handler = () => void;

export const SINGLE_KEY_SHORTCUTS_KEY = 'single-key-shortcuts';
const SEQUENCE_MS = 1500;

const SINGLE_KEYS: Record<string, ShortcutAction> = {
  '?': 'showShortcuts',
  '/': 'focusQuestion',
  '[': 'previousEvidence',
  ']': 'nextEvidence',
};

const AFTER_G: Record<string, ShortcutAction> = {
  o: 'goOverview',
  p: 'goAsk',
  a: 'goFiles',
  h: 'goHistory',
};

const GO_ACTIONS = Object.values(AFTER_G);

const TEXT_INPUT_TYPES = new Set([
  'button',
  'checkbox',
  'color',
  'file',
  'image',
  'radio',
  'range',
  'reset',
  'submit',
]);

// The latest registration wins, so a screen can take over an action from the shell.
const handlers = new Map<ShortcutAction, Handler[]>();

export function useShortcut(action: ShortcutAction, handler: Handler, enabled = true): void {
  const latest = useRef(handler);
  useEffect(() => {
    latest.current = handler;
  });
  useEffect(() => {
    if (!enabled) {
      return;
    }
    const entry: Handler = () => latest.current();
    handlers.set(action, [...(handlers.get(action) ?? []), entry]);
    return () => {
      const remaining = (handlers.get(action) ?? []).filter((item) => item !== entry);
      if (remaining.length > 0) {
        handlers.set(action, remaining);
      } else {
        handlers.delete(action);
      }
    };
  }, [action, enabled]);
}

function handlerFor(action: ShortcutAction): Handler | undefined {
  return handlers.get(action)?.at(-1);
}

// WCAG 2.1.4: keys without a modifier can be turned off. Kept in memory when storage is blocked.
const preferenceListeners = new Set<() => void>();
let unstoredPreference: boolean | null = null;

export function singleKeyShortcutsEnabled(): boolean {
  try {
    return window.localStorage.getItem(SINGLE_KEY_SHORTCUTS_KEY) !== 'off';
  } catch {
    return unstoredPreference ?? true;
  }
}

export function setSingleKeyShortcuts(enabled: boolean): void {
  try {
    if (enabled) {
      window.localStorage.removeItem(SINGLE_KEY_SHORTCUTS_KEY);
    } else {
      window.localStorage.setItem(SINGLE_KEY_SHORTCUTS_KEY, 'off');
    }
  } catch {
    unstoredPreference = enabled;
  }
  preferenceListeners.forEach((listener) => listener());
}

function subscribePreference(onChange: () => void) {
  preferenceListeners.add(onChange);
  const onStorage = (event: StorageEvent) => {
    if (event.key === SINGLE_KEY_SHORTCUTS_KEY) {
      onChange();
    }
  };
  window.addEventListener('storage', onStorage);
  return () => {
    preferenceListeners.delete(onChange);
    window.removeEventListener('storage', onStorage);
  };
}

export function useSingleKeyShortcuts(): boolean {
  return useSyncExternalStore(subscribePreference, singleKeyShortcutsEnabled);
}

export function isApplePlatform(): boolean {
  return /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent);
}

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  if (target.isContentEditable || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT') {
    return true;
  }
  return target instanceof HTMLInputElement && !TEXT_INPUT_TYPES.has(target.type);
}

// Dialogs and menus own their keys: a letter there must not act on the page behind them.
function insideOverlay(target: EventTarget | null): boolean {
  return (
    target instanceof Element &&
    target.closest('[role="dialog"], [role="alertdialog"], [role="menu"]') !== null
  );
}

// Installed once, by the shell.
export function useShortcutListener(): void {
  const enabled = useSingleKeyShortcuts();
  useEffect(() => {
    let sequenceStartedAt: number | null = null;

    function run(event: KeyboardEvent, action: ShortcutAction) {
      const handler = handlerFor(action);
      if (handler) {
        event.preventDefault();
        handler();
      }
    }

    function onKeyDown(event: KeyboardEvent) {
      if (event.defaultPrevented || event.isComposing) {
        return;
      }
      const modifier = event.metaKey || event.ctrlKey;
      if (modifier && !event.altKey && !event.shiftKey && event.key.toLowerCase() === 'k') {
        run(event, 'openPalette');
        return;
      }
      const inSequence =
        sequenceStartedAt !== null && event.timeStamp - sequenceStartedAt <= SEQUENCE_MS;
      sequenceStartedAt = null;
      if (
        !enabled ||
        modifier ||
        event.altKey ||
        isTypingTarget(event.target) ||
        insideOverlay(event.target)
      ) {
        return;
      }
      const key = event.key;
      if (inSequence) {
        const action = AFTER_G[key.toLowerCase()];
        if (action) {
          run(event, action);
        }
        return;
      }
      if (key === 'g' && GO_ACTIONS.some((action) => handlerFor(action))) {
        sequenceStartedAt = event.timeStamp;
        return;
      }
      const action = SINGLE_KEYS[key];
      if (action) {
        run(event, action);
      }
    }

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [enabled]);
}
