import { useEffect, useState } from 'react';
import type { HighlightRequest, HighlightResponse } from './highlight.worker';
import { Highlighting } from './Highlighting';
import type { HighlightLanguage } from './languages';

// Past this, a pathological file is shown as plain text (P8; 1 MB takes about 0.2 s, F0).
export const HIGHLIGHT_TIMEOUT_MS = 1500;

export type HighlightState =
  | { status: 'plain' }
  | { status: 'pending' }
  | { status: 'ready'; highlighting: Highlighting }
  | { status: 'timedOut' };

type Settle = (response: HighlightResponse | 'timedOut') => void;

let worker: Worker | null = null;
let nextId = 0;
const waiting = new Map<number, Settle>();

function getWorker(): Worker {
  if (!worker) {
    const created = new Worker(new URL('./highlight.worker.ts', import.meta.url), {
      type: 'module',
    });
    created.onmessage = (event: MessageEvent<HighlightResponse>) => {
      waiting.get(event.data.id)?.(event.data);
      waiting.delete(event.data.id);
    };
    worker = created;
  }
  return worker;
}

// A parse that runs too long can only be stopped with the worker; requests in flight fall back too.
function restartWorker() {
  worker?.terminate();
  worker = null;
  for (const settle of waiting.values()) {
    settle('timedOut');
  }
  waiting.clear();
}

function request(
  language: HighlightLanguage,
  text: string,
): Promise<HighlightResponse | 'timedOut'> {
  const id = nextId++;
  return new Promise((resolve) => {
    const timer = window.setTimeout(restartWorker, HIGHLIGHT_TIMEOUT_MS);
    waiting.set(id, (response) => {
      window.clearTimeout(timer);
      resolve(response);
    });
    getWorker().postMessage({ id, language, text } satisfies HighlightRequest);
  });
}

// The plain text shows at once; the colors arrive when the worker answers.
export function useHighlight(language: HighlightLanguage | null, text: string): HighlightState {
  const supported = language !== null && typeof Worker !== 'undefined';
  const [result, setResult] = useState<{ key: string; state: HighlightState } | null>(null);
  const key = `${language ?? ''}\u0000${text}`;

  useEffect(() => {
    if (!supported) {
      return;
    }
    let current = true;
    void request(language, text).then((response) => {
      if (!current) {
        return;
      }
      const state: HighlightState =
        response === 'timedOut'
          ? { status: 'timedOut' }
          : 'failed' in response
            ? { status: 'plain' }
            : { status: 'ready', highlighting: new Highlighting(text, response) };
      setResult({ key, state });
    });
    return () => {
      current = false;
    };
  }, [supported, language, text, key]);

  if (!supported) {
    return { status: 'plain' };
  }
  return result?.key === key ? result.state : { status: 'pending' };
}
